package com.hopcape.odo.feature.servicelog.presentation.share

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.Entitlements
import com.hopcape.odo.core.domain.entitlement.Plan
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.feature.servicelog.domain.usecase.ObserveEntryDetailUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.ObserveServiceLogFeedUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.ObserveServiceRecordUseCase
import com.hopcape.odo.feature.servicelog.presentation.FakeCarRepository
import com.hopcape.odo.feature.servicelog.presentation.FakeDocumentRepository
import com.hopcape.odo.feature.servicelog.presentation.FakeHealthScoreRepository
import com.hopcape.odo.feature.servicelog.presentation.FakeOwnerProfileRepository
import com.hopcape.odo.feature.servicelog.presentation.FakeServiceLogRepository
import com.hopcape.odo.feature.servicelog.presentation.NoopAnalytics
import com.hopcape.odo.feature.servicelog.presentation.NoopLogger
import com.hopcape.odo.feature.servicelog.presentation.ServiceLogTelemetry
import com.hopcape.odo.feature.servicelog.presentation.TEST_CAR
import com.hopcape.odo.feature.servicelog.presentation.TEST_CLOCK
import com.hopcape.odo.feature.servicelog.presentation.FixedIdGenerator
import com.hopcape.odo.feature.servicelog.presentation.testCar
import com.hopcape.odo.feature.servicelog.presentation.testEntry
import com.hopcape.odo.feature.servicelog.presentation.share.pdf.ServiceRecordDocument
import com.hopcape.odo.feature.servicelog.presentation.share.pdf.ServiceRecordDocumentFactory
import com.hopcape.odo.feature.servicelog.presentation.testOwner
import com.hopcape.performance.api.APM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import com.hopcape.odo.core.domain.record.entitlement.ExportCredits
import com.hopcape.odo.core.domain.subscription.OneTimePurchaser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.hopcape.odo.core.domain.record.entitlement.RecordExportUsage

/**
 * The share sheet's rules, none of which are about how a PDF is drawn.
 *
 * What matters here is that the owner cannot start two renders, that a second target reuses
 * the document the first one produced, that a failure is reported rather than swallowed, and
 * that a record which changes underneath the sheet invalidates the file written from it.
 */
class ShareRecordViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Records what was written, and can be told to fail. */
    private class RecordingFileStore(private val failing: Boolean = false) : PlatformFileStore {
        val written = mutableListOf<Pair<String, Int>>()

        override suspend fun save(pickedRef: String, directory: String, fileName: String): Either<DomainError, String> =
            DomainError.PersistenceFailure("unused").left()

        override suspend fun delete(storageKey: String) = Unit
        override suspend fun exists(storageKey: String): Boolean = written.any { it.first == storageKey }
        override suspend fun bytes(storageKey: String): Either<DomainError, ByteArray> =
            DomainError.PersistenceFailure("unused").left()

        override suspend fun write(storageKey: String, bytes: ByteArray): Either<DomainError, String> =
            if (failing) {
                DomainError.PersistenceFailure("disk full").left()
            } else {
                written += storageKey to bytes.size
                storageKey.right()
            }
    }

    private fun telemetry() = ServiceLogTelemetry(
        logger = NoopLogger,
        analytics = NoopAnalytics,
        tracer = APM.asTracer(),
        ids = FixedIdGenerator("trace-1"),
    )

    private fun viewModel(
        logs: FakeServiceLogRepository = FakeServiceLogRepository(
            listOf(testEntry("a", km = 54_000, paise = 320_000, verified = true, date = LocalDate(2026, 7, 12))),
        ),
        files: PlatformFileStore = RecordingFileStore(),
        logId: ServiceLogId? = null,
        isPro: Boolean = true,
        exportsUsed: Int = 0,
    ) = ShareRecordViewModel(
        carId = TEST_CAR,
        logId = logId,
        observeRecord = ObserveServiceRecordUseCase(
            cars = FakeCarRepository(testCar()),
            logs = logs,
            documents = FakeDocumentRepository(),
            scores = FakeHealthScoreRepository(),
            owners = FakeOwnerProfileRepository(testOwner()),
            clock = TEST_CLOCK,
            timeZone = TimeZone.UTC,
        ),
        observeDetail = ObserveEntryDetailUseCase(observeFeed = ObserveServiceLogFeedUseCase(logs = logs)),
        entitlements = entitlementsOf(isPro),
        exportCredits = FakeExportCredits(),
        oneTimePurchaser = FakeOneTimePurchaser(),
        exportUsage = FakeRecordExportUsage(used = exportsUsed),
        documents = { record -> ServiceRecordDocument(html = "<!doctype html>${record.carName}", name = "${record.carName} service record") },
        bills = { record, entry -> ServiceRecordDocument(html = "<!doctype html>bill:${entry.id.value}", name = "${record.carName} service bill") },
        files = files,
        telemetry = telemetry(),
    )

    /** A tally that starts where the test wants it and counts what the sheet charges. */
    private class FakeRecordExportUsage(private var used: Int) : RecordExportUsage {
        var charged = 0
            private set

        override suspend fun used(): Int = used

        override suspend fun recordExport() {
            used++
            charged++
        }
    }

    /** A plan that stands still, so the gate is the only thing under test. */
    private fun entitlementsOf(isPro: Boolean) = object : EntitlementSource {
        override fun observe() = flowOf(Entitlements(if (isPro) Plan.PRO else Plan.FREE))
        override suspend fun refresh() = Unit
    }

    private val renderedBytes = "%PDF-1.4 fake".encodeToByteArray()

    /* ------------------------------ the Pro gate ------------------------------ */

    @Test
    fun `a free owner with allowance left gets the export not the paywall`() = runTest {
        val files = RecordingFileStore()
        val viewModel = viewModel(files = files, isPro = false, exportsUsed = 2)
        advanceUntilIdle()
        val effects = mutableListOf<ShareRecordEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.DOWNLOAD))
        advanceUntilIdle()

        assertTrue(
            effects.none { it is ShareRecordEffect.OpenPaywall },
            "the third of three free exports is still free",
        )
    }

    /**
     * #246: out of free exports is no longer a one-way trip to a subscription. Both routes
     * are offered, priced, and nothing is rendered until one is taken.
     */
    @Test
    fun `a free owner who has spent the allowance is offered both routes`() = runTest {
        val files = RecordingFileStore()
        val viewModel = viewModel(files = files, isPro = false, exportsUsed = 3)
        advanceUntilIdle()

        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.DOWNLOAD))
        advanceUntilIdle()

        val offer = assertNotNull(viewModel.state.value.exportOffer)
        assertEquals("Rs. 99", offer.oneTimePrice)
        assertTrue(files.written.isEmpty(), "nothing is rendered for an export that is not allowed")
    }

    @Test
    fun `the Pro route from that offer opens the paywall`() = runTest {
        val viewModel = viewModel(isPro = false, exportsUsed = 3)
        advanceUntilIdle()
        val effects = mutableListOf<ShareRecordEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.DOWNLOAD))
        advanceUntilIdle()
        viewModel.onEvent(ShareRecordEvent.UnlockWithProClicked)
        advanceUntilIdle()

        assertEquals(listOf<ShareRecordEffect>(ShareRecordEffect.OpenPaywall), effects)
    }

    /** Buying takes the share they originally asked for, rather than making them tap again. */
    @Test
    fun `buying one export spends it immediately and renders the share`() = runTest {
        val files = RecordingFileStore()
        val viewModel = viewModel(files = files, isPro = false, exportsUsed = 3)
        advanceUntilIdle()

        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.DOWNLOAD))
        advanceUntilIdle()
        viewModel.onEvent(ShareRecordEvent.BuyExportClicked)
        advanceUntilIdle()

        assertNull(viewModel.state.value.exportOffer, "the offer closes once it is answered")
    }

    @Test
    fun `a Pro owner is never stopped however many they have taken`() = runTest {
        val files = RecordingFileStore()
        val viewModel = viewModel(files = files, isPro = true, exportsUsed = 99)
        advanceUntilIdle()
        val effects = mutableListOf<ShareRecordEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.DOWNLOAD))
        advanceUntilIdle()

        assertTrue(effects.none { it is ShareRecordEffect.OpenPaywall })
    }

    @Test
    fun `sharing one entry's bill is never gated`() = runTest {
        // It is the bill they just paid. Charging for their own receipt would be the wrong
        // line to draw.
        val viewModel = viewModel(logId = ServiceLogId("a"), isPro = false)
        advanceUntilIdle()
        val effects = mutableListOf<ShareRecordEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.DOWNLOAD))
        advanceUntilIdle()

        assertTrue(effects.none { it is ShareRecordEffect.OpenPaywall })
        assertTrue(effects.any { it is ShareRecordEffect.RenderDocument })
    }

    @Test
    fun `the sheet reports the counts the record holds`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        val content = viewModel.state.value.content
        assertTrue(content is ShareRecordUiState.Content.Loaded)
        assertEquals("Maruti Swift VXI", content.carName)
        // The service plus the car's opening milestone.
        assertEquals(2, content.serviceCount)
        assertEquals(1, content.verifiedCount)
    }

    @Test
    fun `tapping a target asks the host to render and the sheet goes busy`() = runTest {
        val viewModel = viewModel()
        val effects = mutableListOf<ShareRecordEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        advanceUntilIdle()

        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.WHATSAPP))
        advanceUntilIdle()

        val render = effects.filterIsInstance<ShareRecordEffect.RenderDocument>().single()
        assertEquals(ShareTarget.WHATSAPP, render.target)
        assertTrue(render.html.startsWith("<!doctype html>"), "the host is handed a whole document")
        assertEquals(ExportUiState.Rendering(ShareTarget.WHATSAPP), viewModel.state.value.export)
        assertTrue(viewModel.state.value.isBusy)
    }

    @Test
    fun `a second tap while rendering is ignored`() = runTest {
        val viewModel = viewModel()
        val effects = mutableListOf<ShareRecordEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        advanceUntilIdle()

        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.WHATSAPP))
        advanceUntilIdle()
        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.EMAIL))
        advanceUntilIdle()

        assertEquals(
            1,
            effects.filterIsInstance<ShareRecordEffect.RenderDocument>().size,
            "one document, however many times the owner taps",
        )
    }

    @Test
    fun `a rendered document is written and then shared`() = runTest {
        val files = RecordingFileStore()
        val viewModel = viewModel(files = files)
        val effects = mutableListOf<ShareRecordEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        advanceUntilIdle()

        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.DOWNLOAD))
        advanceUntilIdle()
        viewModel.onEvent(ShareRecordEvent.Rendered(renderedBytes, ShareTarget.DOWNLOAD))
        advanceUntilIdle()

        val share = effects.filterIsInstance<ShareRecordEffect.ShareFile>().single()
        assertEquals("exports/${TEST_CAR.value}/service-record.pdf", share.storageKey)
        assertEquals(listOf(share.storageKey to renderedBytes.size), files.written)
        assertEquals(ExportUiState.Idle, viewModel.state.value.export, "the sheet is usable again")
        assertTrue(share.title.isNotBlank(), "the share sheet offers the file under a name")
    }

    @Test
    fun `a second target reuses the document already produced`() = runTest {
        val files = RecordingFileStore()
        val viewModel = viewModel(files = files)
        val effects = mutableListOf<ShareRecordEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        advanceUntilIdle()

        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.WHATSAPP))
        advanceUntilIdle()
        viewModel.onEvent(ShareRecordEvent.Rendered(renderedBytes, ShareTarget.WHATSAPP))
        advanceUntilIdle()
        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.EMAIL))
        advanceUntilIdle()

        assertEquals(
            1,
            effects.filterIsInstance<ShareRecordEffect.RenderDocument>().size,
            "the record has not changed, so there is nothing to render again",
        )
        assertEquals(2, effects.filterIsInstance<ShareRecordEffect.ShareFile>().size)
        assertEquals(1, files.written.size, "and nothing to write again")
    }

    @Test
    fun `a render that produced nothing is reported as a failure`() = runTest {
        val viewModel = viewModel()
        val effects = mutableListOf<ShareRecordEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        advanceUntilIdle()

        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.MORE))
        advanceUntilIdle()
        viewModel.onEvent(ShareRecordEvent.Rendered(bytes = null, target = ShareTarget.MORE))
        advanceUntilIdle()

        assertEquals(ExportUiState.Failed, viewModel.state.value.export)
        assertTrue(effects.none { it is ShareRecordEffect.ShareFile }, "there is no file to share")
    }

    @Test
    fun `an empty render is a failure not an empty document`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.MORE))
        advanceUntilIdle()
        viewModel.onEvent(ShareRecordEvent.Rendered(bytes = ByteArray(0), target = ShareTarget.MORE))
        advanceUntilIdle()

        assertEquals(ExportUiState.Failed, viewModel.state.value.export)
    }

    @Test
    fun `a file that cannot be written is a failure`() = runTest {
        val viewModel = viewModel(files = RecordingFileStore(failing = true))
        val effects = mutableListOf<ShareRecordEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        advanceUntilIdle()

        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.DOWNLOAD))
        advanceUntilIdle()
        viewModel.onEvent(ShareRecordEvent.Rendered(renderedBytes, ShareTarget.DOWNLOAD))
        advanceUntilIdle()

        assertEquals(ExportUiState.Failed, viewModel.state.value.export)
        assertTrue(effects.none { it is ShareRecordEffect.ShareFile })
    }

    @Test
    fun `a service logged while the sheet is open invalidates the written document`() = runTest {
        val logs = FakeServiceLogRepository(
            listOf(testEntry("a", km = 54_000, paise = 320_000, verified = true, date = LocalDate(2026, 7, 12))),
        )
        val viewModel = viewModel(logs = logs)
        val effects = mutableListOf<ShareRecordEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        advanceUntilIdle()

        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.WHATSAPP))
        advanceUntilIdle()
        viewModel.onEvent(ShareRecordEvent.Rendered(renderedBytes, ShareTarget.WHATSAPP))
        advanceUntilIdle()

        logs.entries.value = logs.entries.value + testEntry("b", km = 60_000, date = LocalDate(2026, 8, 1))
        advanceUntilIdle()

        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.EMAIL))
        advanceUntilIdle()

        assertEquals(
            2,
            effects.filterIsInstance<ShareRecordEffect.RenderDocument>().size,
            "the file written from the older record would be missing the new entry",
        )
    }

    /* ------------------------- the bill share ------------------------- */

    @Test
    fun `opened on one entry the sheet describes that bill rather than the record`() = runTest {
        val viewModel = viewModel(logId = ServiceLogId("a"))
        advanceUntilIdle()

        val content = viewModel.state.value.content
        assertTrue(content is ShareRecordUiState.Content.LoadedBill)
        assertEquals("Maruti Swift VXI", content.carName)
        assertEquals(LocalDate(2026, 7, 12), content.serviceDate)
        assertEquals(320_000, content.amount.paise)
    }

    @Test
    fun `opened on one entry the document rendered is the bill not the record`() = runTest {
        val files = RecordingFileStore()
        val viewModel = viewModel(files = files, logId = ServiceLogId("a"))
        val effects = mutableListOf<ShareRecordEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        advanceUntilIdle()

        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.DOWNLOAD))
        advanceUntilIdle()

        val render = effects.filterIsInstance<ShareRecordEffect.RenderDocument>().single()
        assertEquals("<!doctype html>bill:a", render.html, "the single entry's bill is what goes out")

        viewModel.onEvent(ShareRecordEvent.Rendered(renderedBytes, ShareTarget.DOWNLOAD))
        advanceUntilIdle()

        val share = effects.filterIsInstance<ShareRecordEffect.ShareFile>().single()
        assertEquals(
            "exports/${TEST_CAR.value}/service-bill.pdf",
            share.storageKey,
            "the bill is written beside the record, never over it",
        )
    }

    @Test
    fun `an entry that does not exist shares nothing`() = runTest {
        val viewModel = viewModel(logId = ServiceLogId("missing"))
        val effects = mutableListOf<ShareRecordEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        advanceUntilIdle()

        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.WHATSAPP))
        advanceUntilIdle()

        assertEquals(ShareRecordUiState.Content.Loading, viewModel.state.value.content)
        assertTrue(
            effects.none { it is ShareRecordEffect.RenderDocument },
            "there is no bill to print",
        )
    }

    @Test
    fun `nothing is shared before the record has loaded`() = runTest {
        val viewModel = viewModel()
        val effects = mutableListOf<ShareRecordEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

        // No advanceUntilIdle: the observe has not emitted yet.
        viewModel.onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.WHATSAPP))
        advanceUntilIdle()

        assertTrue(
            effects.none { it is ShareRecordEffect.RenderDocument },
            "there is no record to print",
        )
    }
    /** No credits bought, and nothing here spends one — the free-allowance path is what these test. */
    private class FakeExportCredits : ExportCredits {
        var granted = 0
        override suspend fun available(): Int = granted
        override suspend fun grant() {
            granted++
        }

        override suspend fun spend(): Boolean = if (granted > 0) { granted--; true } else false
    }

    private class FakeOneTimePurchaser : OneTimePurchaser {
        override suspend fun purchase(productId: String) = Unit.right()
        override suspend fun priceOf(productId: String): String = "Rs. 99"
    }

}
