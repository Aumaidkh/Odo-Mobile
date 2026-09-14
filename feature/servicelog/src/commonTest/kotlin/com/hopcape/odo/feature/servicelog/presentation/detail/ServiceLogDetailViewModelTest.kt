package com.hopcape.odo.feature.servicelog.presentation.detail

import arrow.core.left
import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.fairness.model.FairnessOutcome
import com.hopcape.odo.core.config.FeatureConfig
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.showcase.ShowcaseArbiter
import com.hopcape.odo.core.domain.showcase.ShowcaseHookId
import com.hopcape.odo.core.domain.showcase.ShowcaseSeenStore
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.verification
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.feature.servicelog.domain.usecase.AttachBillPhotoUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.DeleteServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.ObserveEntryDetailUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.ObserveServiceLogFeedUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.RecordEntryFairnessUseCase
import com.hopcape.odo.feature.servicelog.presentation.FakeServiceLogRepository
import com.hopcape.odo.feature.servicelog.presentation.ServiceLogTelemetry
import com.hopcape.odo.feature.servicelog.presentation.TEST_CAR
import com.hopcape.odo.feature.servicelog.presentation.TEST_CITY
import com.hopcape.odo.feature.servicelog.presentation.TEST_CLOCK
import com.hopcape.odo.feature.servicelog.presentation.testEntry
import com.hopcape.odo.feature.servicelog.presentation.testResolveFairness
import com.hopcape.performance.api.APM
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceLogDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun attachingABill_verifiesTheEntryAndRecordsWhatTheCitySaid() = runTest(dispatcher) {
        val logs = FakeServiceLogRepository(listOf(selfReported()))
        val viewModel = viewModel(logs)
        advanceUntilIdle()

        viewModel.onEvent(ServiceLogDetailEvent.BillPicked("content://picked/photo.jpg"))
        advanceUntilIdle()

        val stored = logs.entries.value.single()
        assertEquals(VerificationStatus.VERIFIED, stored.verification)
        assertEquals("bills/car-1/log-1.jpg", stored.billPhotoRef)
        // Rs. 3,300 paid against a Rs. 2,400 average.
        val over = assertIs<FairnessOutcome.Over>(stored.fairness?.outcome)
        assertEquals(90_000L, over.by.paise)
    }

    @Test
    fun aFailedAttach_saysSoAndLeavesTheEntryAlone() = runTest(dispatcher) {
        val logs = FakeServiceLogRepository(listOf(selfReported()))
        val viewModel = viewModel(logs, files = RefusingFileStore)
        advanceUntilIdle()

        viewModel.onEvent(ServiceLogDetailEvent.BillPicked("content://gone"))
        advanceUntilIdle()

        assertIs<AttachUiState.Failed>(viewModel.state.value.attach)
        assertEquals(VerificationStatus.SELF_REPORTED, logs.entries.value.single().verification)
    }

    @Test
    fun withNoCityYet_theBillStillAttaches() = runTest(dispatcher) {
        // Benchmarking needs a city; verifying the entry does not. Losing the photo over a
        // profile field the owner never filled in would be the wrong trade.
        val logs = FakeServiceLogRepository(listOf(selfReported()))
        val viewModel = viewModel(logs, city = null)
        advanceUntilIdle()

        viewModel.onEvent(ServiceLogDetailEvent.BillPicked("content://picked/photo.jpg"))
        advanceUntilIdle()

        val stored = logs.entries.value.single()
        assertEquals(VerificationStatus.VERIFIED, stored.verification)
        assertNull(stored.fairness)
    }

    @Test
    fun attachingAsksForThePicker() = runTest(dispatcher) {
        val viewModel = viewModel(FakeServiceLogRepository(listOf(selfReported())))
        advanceUntilIdle()

        viewModel.onEvent(ServiceLogDetailEvent.AttachBillClicked)

        assertEquals(ServiceLogDetailEffect.PickBillPhoto, viewModel.effects.first())
    }

    @Test
    fun aVerifiedUncheckedEntry_raisesTheFairnessShowcase_andActingOnItOpensTheCheck() = runTest(dispatcher) {
        val viewModel = viewModel(FakeServiceLogRepository(listOf(verified())))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.fairnessShowcase)

        viewModel.onEvent(ServiceLogDetailEvent.FairnessShowcaseActedOn)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.fairnessShowcase)
        assertIs<ServiceLogDetailEffect.OpenFairness>(viewModel.effects.first())
    }

    @Test
    fun aSelfReportedEntry_neverRaisesTheFairnessShowcase() = runTest(dispatcher) {
        val viewModel = viewModel(FakeServiceLogRepository(listOf(selfReported())))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.fairnessShowcase)
    }

    @Test
    fun checkFairness_handsTheEntrysOwnLinesToTheSharedReport() = runTest(dispatcher) {
        val viewModel = viewModel(FakeServiceLogRepository(listOf(verified())))
        advanceUntilIdle()

        viewModel.onEvent(ServiceLogDetailEvent.CheckFairnessClicked)

        val effect = assertIs<ServiceLogDetailEffect.OpenFairness>(viewModel.effects.first())
        assertEquals(ServiceLogId("log-1"), effect.id)
        val line = effect.lines.single()
        assertEquals(ServiceCategory.BRAKES.name, line.categoryName)
        assertEquals(330_000L, line.amountPaise)
    }

    @Test
    fun withNothingComparable_theCheckIsNotOffered_andOpensNoReport() = runTest(dispatcher) {
        // Two category tags over one total: there is no way to know which share belongs to
        // which job, so there is nothing honest to benchmark.
        val entry = testEntry(
            id = "log-1",
            km = 40_000,
            paise = 330_000,
            verified = true,
            categories = setOf(ServiceCategory.BRAKES, ServiceCategory.OIL_CHANGE),
        )
        val viewModel = viewModel(FakeServiceLogRepository(listOf(entry)))
        advanceUntilIdle()

        // The screen says so rather than leaving the owner with a button that refuses in
        // silence (issue #111) — it draws the action disabled, with the reason under it.
        val loaded = assertIs<ServiceLogDetailUiState.Content.Loaded>(viewModel.state.value.content)
        assertFalse(loaded.entry.canCheckFairness)

        // And the guard behind it holds: nothing opens even if the event arrives anyway.
        viewModel.onEvent(ServiceLogDetailEvent.CheckFairnessClicked)

        assertNull(withTimeoutOrNull(TIMEOUT_MILLIS) { viewModel.effects.first() })
    }

    /* ------------------------- fixtures ------------------------- */

    private fun selfReported() = testEntry(
        id = "log-1",
        km = 40_000,
        paise = 330_000,
        categories = setOf(ServiceCategory.BRAKES),
    )

    @Test
    fun withTheBillCheckGatedOffTheButtonIsGoneAndTheActionRefuses() = runTest(dispatcher) {
        val logs = FakeServiceLogRepository(listOf(verified()))
        val viewModel = viewModel(logs, billCheckEnabled = false)
        val effects = mutableListOf<ServiceLogDetailEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        advanceUntilIdle()

        assertFalse(viewModel.state.value.showBillCheck)

        // The invariant behind the button, not a second copy of it: a caller that skipped
        // the screen must not open a check the switch has closed.
        viewModel.onEvent(ServiceLogDetailEvent.CheckFairnessClicked)
        advanceUntilIdle()
        assertTrue(effects.none { it is ServiceLogDetailEffect.OpenFairness })
    }

    /**
     * The coach mark points at the button. With the gate closed the button never composes,
     * so a granted hook is one nobody sees — and it holds the app-wide one-at-a-time grant.
     */
    @Test
    fun withTheBillCheckGatedOffTheCoachMarkIsNeverAskedFor() = runTest(dispatcher) {
        val logs = FakeServiceLogRepository(listOf(verified()))
        val viewModel = viewModel(logs, billCheckEnabled = false)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.fairnessShowcase)
    }

    private fun verified() = testEntry(
        id = "log-1",
        km = 40_000,
        paise = 330_000,
        verified = true,
        categories = setOf(ServiceCategory.BRAKES),
    )

    private fun viewModel(
        logs: FakeServiceLogRepository,
        files: PlatformFileStore = CopyingFileStore,
        city: String? = "Pune",
        billCheckEnabled: Boolean = true,
    ) = ServiceLogDetailViewModel(
        carId = TEST_CAR,
        logId = ServiceLogId("log-1"),
        observeDetail = ObserveEntryDetailUseCase(ObserveServiceLogFeedUseCase(logs)),
        deleteLog = DeleteServiceLogUseCase(logs),
        attachBillPhoto = AttachBillPhotoUseCase(logs, files),
        recordFairness = RecordEntryFairnessUseCase(
            logs = logs,
            resolveFairness = testResolveFairness(mapOf(ServiceCategory.BRAKES to (240_000L to 30))),
            currentCity = if (city == null) CurrentCityProvider { null } else TEST_CITY,
            clock = TEST_CLOCK,
        ),
        showcase = ShowcaseArbiter(FakeShowcaseSeenStore()),
        telemetry = ServiceLogTelemetry(
            logger = HLogger.asLogger(),
            analytics = SilentAnalytics,
            tracer = APM.asTracer(),
            ids = FixedIdGenerator(),
        ),
        config = billCheckConfig(enabled = billCheckEnabled),
    )

    /** The bill check's launch gate. On for every test that is not about the gate itself. */
    private fun billCheckConfig(enabled: Boolean) = object : FeatureConfig {
        override val autoOdometerEnabled = true
        override val refuelDetectEnabled = true
        override val challanEnabled = false
        override val plateLookupEnabled = false
        override val advisoryClassifierEnabled = false
        override val billCheckEnabled = enabled
        override val serviceChecklistEnabled = false
    }

    private class FakeShowcaseSeenStore : ShowcaseSeenStore {
        val seen = mutableSetOf<ShowcaseHookId>()
        override suspend fun isSeen(hook: ShowcaseHookId): Boolean = hook in seen
        override suspend fun markSeen(hook: ShowcaseHookId) {
            seen += hook
        }

        override suspend fun clearAll() = seen.clear()
    }

    /** Copies nothing, but answers with the key the real store would have written to. */
    private object CopyingFileStore : PlatformFileStore {
        override suspend fun save(pickedRef: String, directory: String, fileName: String) =
            "$directory/$fileName.jpg".right()

        override suspend fun delete(storageKey: String) = Unit
        override suspend fun exists(storageKey: String) = true
        override suspend fun bytes(storageKey: String) = ByteArray(0).right()

        override suspend fun write(storageKey: String, bytes: ByteArray) = storageKey.right()
    }

    private object RefusingFileStore : PlatformFileStore {
        override suspend fun save(pickedRef: String, directory: String, fileName: String) =
            DomainError.PersistenceFailure("no bytes").left()

        override suspend fun delete(storageKey: String) = Unit
        override suspend fun exists(storageKey: String) = false
        override suspend fun bytes(storageKey: String) =
            DomainError.PersistenceFailure("no bytes").left()

        override suspend fun write(storageKey: String, bytes: ByteArray) =
            DomainError.PersistenceFailure("no bytes").left()
    }

    private class FixedIdGenerator(private val id: String = "trace") : IdGenerator {
        override fun newId(): String = id
    }

    private companion object {
        const val TIMEOUT_MILLIS = 1_000L
    }

    private object SilentAnalytics : AnalyticsTracker {
        override fun identify(traits: UserTraits) = Unit
        override fun track(eventName: String, properties: Map<String, Any?>) = Unit
        override fun setConsent(status: ConsentStatus) = Unit
        override fun flush() = Unit
    }
}
