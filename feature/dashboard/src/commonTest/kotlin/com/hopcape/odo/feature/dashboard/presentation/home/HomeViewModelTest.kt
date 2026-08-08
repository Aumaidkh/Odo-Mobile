package com.hopcape.odo.feature.dashboard.presentation.home

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.health.model.HealthBand
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.feature.dashboard.FakeActiveCarProvider
import com.hopcape.odo.feature.dashboard.FakeCarRepository
import com.hopcape.odo.feature.dashboard.FakeCurrentCityProvider
import com.hopcape.odo.feature.dashboard.FakeDocumentRepository
import com.hopcape.odo.feature.dashboard.FakeFuelPriceProvider
import com.hopcape.odo.feature.dashboard.FakeHealthScoreRepository
import com.hopcape.odo.feature.dashboard.FakeOwnerProfileRepository
import com.hopcape.odo.feature.dashboard.FakeServiceLogRepository
import com.hopcape.odo.feature.dashboard.FixedClock
import com.hopcape.odo.feature.dashboard.TEST_CAR
import com.hopcape.odo.feature.dashboard.currentOdometerFrom
import com.hopcape.odo.feature.dashboard.domain.usecase.ObserveHomeUseCase
import com.hopcape.odo.feature.dashboard.presentation.state.Loadable
import com.hopcape.odo.feature.dashboard.testDocument
import com.hopcape.odo.feature.dashboard.testEntry
import com.hopcape.performance.api.APM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** A car with a record: two services, insurance on file, one bill attached. */
    private val entries = listOf(
        testEntry("l1", LocalDate(2026, 7, 12), verified = true),
        testEntry("l2", LocalDate(2026, 6, 21)),
    )
    private val documents = listOf(testDocument(DocumentType.INSURANCE))

    /* ------------------------- state ------------------------- */

    @Test
    fun showsTheScoredDashboardForACarWithARecord() = runTest(dispatcher) {
        val content = viewModel().content()

        assertEquals("Rahul", content.userName)
        assertEquals("Maruti Suzuki Swift VXI", content.carName)
        assertEquals(false, content.isNewUser)
        assertEquals(false, content.hasNoCar)
        // Two services on time and insurance on file, with no PUC and one bill of two.
        assertEquals(HealthBand.FAIR, content.band)
    }

    @Test
    fun withNoCarYetHomeAsksForOne() = runTest(dispatcher) {
        val content = viewModel(carId = null).content()

        assertTrue(content.hasNoCar)
        assertEquals("", content.carName)
    }

    @Test
    fun aCarWithNothingOnItGetsTheChecklist() = runTest(dispatcher) {
        val content = viewModel(entries = emptyList(), documents = emptyList()).content()

        assertTrue(content.isNewUser)
        assertEquals(1, content.setup.doneCount)
    }

    @Test
    fun theDashboardFollowsTheLogAsItChanges() = runTest(dispatcher) {
        val logs = FakeServiceLogRepository(emptyList())
        val viewModel = viewModel(logs = logs, documents = emptyList())
        assertTrue(viewModel.content().isNewUser)

        logs.emit(listOf(testEntry("l1", LocalDate(2026, 7, 30))))
        advanceUntilIdle()

        assertEquals(false, viewModel.ready().isNewUser)
    }

    /* ------------------------- navigation ------------------------- */

    @Test
    fun theBreakdownLinkOpensTheHealthScore() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.content()

        viewModel.onEvent(HomeEvent.BreakdownTapped)

        assertEquals(HomeEffect.OpenHealthScore, viewModel.effects.first())
    }

    /** A paper is renewed in the vault. */
    @Test
    fun aDocumentAttentionLeadsToTheVault() = runTest(dispatcher) {
        val viewModel = viewModel(
            documents = listOf(testDocument(DocumentType.PUC, expiresOn = LocalDate(2026, 7, 25))),
        )
        viewModel.content()

        viewModel.onEvent(HomeEvent.AttentionTapped)

        assertEquals(HomeEffect.OpenVault, viewModel.effects.first())
    }

    /** A service is dealt with in the log, which is where the next entry gets added. */
    @Test
    fun aServiceAttentionLeadsToTheServiceLog() = runTest(dispatcher) {
        val viewModel = viewModel(
            entries = listOf(testEntry("old", LocalDate(2025, 1, 1))),
            documents = emptyList(),
        )
        viewModel.content()

        viewModel.onEvent(HomeEvent.AttentionTapped)

        assertEquals(HomeEffect.OpenServiceLog(carId = TEST_CAR.value), viewModel.effects.first())
    }

    @Test
    fun theRecentRowOpensOnlyAService() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.content()

        viewModel.onEvent(HomeEvent.RecentTapped)

        assertEquals(
            HomeEffect.OpenService(logId = "l1", carId = TEST_CAR.value),
            viewModel.effects.first(),
        )
    }

    @Test
    fun theBillFunnelReachesTheScannerFromEitherPath() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.content()

        viewModel.onEvent(HomeEvent.ScanBillTapped)

        assertEquals(HomeEffect.OpenScanner, viewModel.effects.first())
    }

    @Test
    fun addCarLeadsToTheGaragesAddFlow() = runTest(dispatcher) {
        val viewModel = viewModel(carId = null)
        viewModel.content()

        viewModel.onEvent(HomeEvent.AddCarTapped)

        assertEquals(HomeEffect.OpenAddCar, viewModel.effects.first())
    }

    /* ------------------------- telemetry ------------------------- */

    @Test
    fun opensAreReportedOnceWithTheShapeOfTheDashboard() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)
        viewModel.content()
        advanceUntilIdle()

        val opened = analytics.events.filter { it.first == HomeTelemetry.Event.OPENED }
        assertEquals(1, opened.size)
        assertEquals(false, opened.single().second[HomeTelemetry.Key.IS_NEW_USER])
        assertEquals(3, opened.single().second[HomeTelemetry.Key.SETUP_DONE])
    }

    @Test
    fun theAttentionTapCarriesWhatKindOfDeadlineItWas() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(
            documents = listOf(testDocument(DocumentType.PUC, expiresOn = LocalDate(2026, 7, 25))),
            analytics = analytics,
        )
        viewModel.content()

        viewModel.onEvent(HomeEvent.AttentionTapped)
        advanceUntilIdle()

        val tapped = analytics.events.single { it.first == HomeTelemetry.Event.ATTENTION_TAPPED }
        assertEquals("DocumentLapsed", tapped.second[HomeTelemetry.Key.KIND])
    }

    /** Nothing to act on means nothing to report and nowhere to go. */
    @Test
    fun tappingAnAllClearCardDoesNothing() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)
        val content = viewModel.content()
        assertNull(content.attention)

        viewModel.onEvent(HomeEvent.AttentionTapped)
        advanceUntilIdle()

        assertEquals(0, analytics.events.count { it.first == HomeTelemetry.Event.ATTENTION_TAPPED })
    }

    @Test
    fun theFirstScanIsReportedAsComingFromTheChecklist() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(entries = emptyList(), documents = emptyList(), analytics = analytics)
        viewModel.content()

        viewModel.onEvent(HomeEvent.ScanBillTapped)
        advanceUntilIdle()

        val scan = analytics.events.single { it.first == HomeTelemetry.Event.SCAN_BILL_TAPPED }
        assertEquals(true, scan.second[HomeTelemetry.Key.FROM_CHECKLIST])
    }

    /* ------------------------- fixtures ------------------------- */

    private fun viewModel(
        carId: CarId? = TEST_CAR,
        entries: List<ServiceLogEntry> = this.entries,
        documents: List<Document> = this.documents,
        logs: FakeServiceLogRepository = FakeServiceLogRepository(entries),
        analytics: RecordingAnalytics = RecordingAnalytics(),
    ) = HomeViewModel(
        activeCar = FakeActiveCarProvider(carId),
        observeHome = ObserveHomeUseCase(
            cars = FakeCarRepository(),
            logs = logs,
            documents = FakeDocumentRepository(documents),
            scores = FakeHealthScoreRepository(),
            owners = FakeOwnerProfileRepository(),
            city = FakeCurrentCityProvider(),
            fuelPrices = FakeFuelPriceProvider(),
            currentOdometer = currentOdometerFrom(logs),
            clock = FixedClock(),
            timeZone = TimeZone.UTC,
        ),
        telemetry = telemetry(analytics),
    )

    private fun telemetry(analytics: RecordingAnalytics) = HomeTelemetry(
        logger = HLogger.asLogger(),
        analytics = analytics,
        tracer = APM.asTracer(),
        ids = FixedIdGenerator(),
    )

    private suspend fun HomeViewModel.content(): HomeContent =
        assertIs<Loadable.Ready<HomeContent>>(state.first { it.content is Loadable.Ready }.content).value

    private fun HomeViewModel.ready(): HomeContent =
        assertIs<Loadable.Ready<HomeContent>>(state.value.content).value

    private class FixedIdGenerator(private val id: String = "trace") : IdGenerator {
        override fun newId(): String = id
    }

    private class RecordingAnalytics : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()

        override fun identify(traits: UserTraits) = Unit
        override fun track(eventName: String, properties: Map<String, Any?>) {
            events += eventName to properties
        }

        override fun setConsent(status: ConsentStatus) = Unit
        override fun flush() = Unit
    }
}
