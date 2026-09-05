package com.hopcape.odo.feature.dashboard.presentation.home

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.config.FeatureConfig
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
import com.hopcape.odo.feature.dashboard.FakeFuelFillRepository
import com.hopcape.odo.feature.dashboard.FakeRefuelDetectionStore
import com.hopcape.odo.feature.dashboard.FakeHealthScoreRepository
import com.hopcape.odo.feature.dashboard.FakeOwnerProfileRepository
import com.hopcape.odo.feature.dashboard.FakeServiceLogRepository
import com.hopcape.odo.feature.dashboard.FixedClock
import com.hopcape.odo.feature.dashboard.TEST_CAR
import com.hopcape.odo.feature.dashboard.currentOdometerFrom
import com.hopcape.odo.core.domain.showcase.ShowcaseArbiter
import com.hopcape.odo.core.domain.showcase.ShowcaseHookId
import com.hopcape.odo.core.domain.showcase.ShowcaseSeenStore
import com.hopcape.odo.core.triptracker.TrackingStatus
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.core.triptracker.TripTracker
import com.hopcape.odo.core.triptracker.VehicleBond
import com.hopcape.odo.core.triptracker.VehicleBondStore
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
import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.Entitlements
import com.hopcape.odo.core.domain.entitlement.Plan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.assertFalse

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

    /**
     * An owner tapping an overdue service has not had it yet — they are about to book one —
     * so the tap leads to the checklist they need walking in, not the log a finished service
     * is recorded in.
     */
    @Test
    fun aServiceAttentionLeadsToTheChecklist() = runTest(dispatcher) {
        val viewModel = viewModel(
            entries = listOf(testEntry("old", LocalDate(2025, 1, 1))),
            documents = emptyList(),
        )
        viewModel.content()

        viewModel.onEvent(HomeEvent.AttentionTapped)

        assertEquals(
            HomeEffect.OpenServiceChecklist(entry = "HOME_ATTENTION"),
            viewModel.effects.first(),
        )
    }

    /**
     * The conditional card's whole reason to exist: a lapsed paper outranks a due service in
     * the attention picker, so without it the checklist would be unreachable from Home
     * exactly when the owner is about to book the service.
     */
    @Test
    fun aLapsedPaperOverAServiceDueStillOffersTheChecklistOnItsOwnCard() = runTest(dispatcher) {
        val viewModel = viewModel(
            entries = listOf(testEntry("old", LocalDate(2025, 1, 1))),
            documents = listOf(testDocument(DocumentType.PUC, expiresOn = LocalDate(2025, 6, 1))),
        )
        viewModel.content()

        assertTrue(viewModel.state.value.offerChecklist)

        viewModel.onEvent(HomeEvent.ChecklistTapped)
        assertEquals(
            HomeEffect.OpenServiceChecklist(entry = "HOME_CARD"),
            viewModel.effects.first(),
        )
    }

    /** When attention is already the service, Home does not say it twice. */
    @Test
    fun aServiceAttentionKeepsTheConditionalCardDown() = runTest(dispatcher) {
        val viewModel = viewModel(
            entries = listOf(testEntry("old", LocalDate(2025, 1, 1))),
            documents = emptyList(),
        )
        viewModel.content()

        assertFalse(viewModel.state.value.offerChecklist)
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

    /**
     * #251: automatic logging is never gated, so the card always opens the explanation —
     * on the free plan, and however many fills have already been detected.
     */
    @Test
    fun autoDetectTapped_alwaysOpensTheExplanation_neverAPaywall() = runTest(dispatcher) {
        val vm = viewModel(isPro = false, detectedFillsUsed = 50)
        vm.state.first { it.content is Loadable.Ready }

        vm.onEvent(HomeEvent.AutoDetectTapped)

        assertIs<HomeEffect.OpenAutoDetect>(vm.effects.first())
    }

    @Test
    fun autoDetectTapped_opensEnrolmentWhenUnlocked() = runTest(dispatcher) {
        val vm = viewModel(isPro = true)
        vm.state.first { it.content is Loadable.Ready }
        vm.onEvent(HomeEvent.AutoDetectTapped)
        assertIs<HomeEffect.OpenAutoDetect>(vm.effects.first())
    }

    @Test
    fun autoOdometerOffer_shownWhileNotSetUp_andGoneOnceEnrolledAndOn() = runTest(dispatcher) {
        val offered = viewModel(bond = null, trackingEnabled = false)
        assertTrue(offered.state.first { it.content is Loadable.Ready }.offerAutoOdometer)

        val setUp = viewModel(
            bond = VehicleBond(carId = TEST_CAR, bluetoothId = "bt-1", triggerMode = TriggerMode.STEREO),
            trackingEnabled = true,
        )
        assertFalse(setUp.state.first { it.content is Loadable.Ready }.offerAutoOdometer)
    }

    /**
     * Both of these were impossible to write until this release. The flags were
     * compile-time consts, so a test could not set one — the off path was only ever
     * exercised by a build that happened to be compiled with the flag off, which is to say
     * never, since both shipped on.
     */
    @Test
    fun autoOdometerOffer_isAbsentWhileTheFlagIsOff() = runTest(dispatcher) {
        val off = viewModel(bond = null, trackingEnabled = false, autoOdometerEnabled = false)

        assertFalse(off.state.first { it.content is Loadable.Ready }.offerAutoOdometer)
    }

    @Test
    fun autoDetectOffer_isAbsentWhileTheFlagIsOff() = runTest(dispatcher) {
        // The flag short-circuits ahead of the detection settings, so this holds whatever
        // those say.
        val off = viewModel(refuelDetectEnabled = false)

        assertFalse(off.state.first { it.content is Loadable.Ready }.offerAutoDetect)
    }

    @Test
    fun scanShowcase_grantedOnAFreshDeviceWithACar_andNothingLogged() = runTest(dispatcher) {
        val vm = viewModel(entries = emptyList(), documents = emptyList())

        assertTrue(vm.state.first { it.content is Loadable.Ready && it.scanShowcase }.scanShowcase)
    }

    @Test
    fun scanShowcase_notGranted_onceSomethingIsLogged() = runTest(dispatcher) {
        val vm = viewModel()

        assertFalse(vm.state.first { it.content is Loadable.Ready }.scanShowcase)
    }

    @Test
    fun scanShowcase_notGranted_whenAlreadySeen() = runTest(dispatcher) {
        val seen = FakeShowcaseSeenStore().apply { seen += ShowcaseHookId.SCAN_BUTTON }
        val vm = viewModel(entries = emptyList(), documents = emptyList(), seenStore = seen)

        assertFalse(vm.state.first { it.content is Loadable.Ready }.scanShowcase)
    }

    @Test
    fun scanShowcaseDismissed_hidesIt_andWritesSeen() = runTest(dispatcher) {
        val seen = FakeShowcaseSeenStore()
        val vm = viewModel(entries = emptyList(), documents = emptyList(), seenStore = seen)
        vm.state.first { it.scanShowcase }

        vm.onEvent(HomeEvent.ScanShowcaseDismissed)

        assertFalse(vm.state.first { !it.scanShowcase }.scanShowcase)
        advanceUntilIdle()
        assertTrue(ShowcaseHookId.SCAN_BUTTON in seen.seen)
    }

    @Test
    fun scanShowcaseActedOn_opensTheScanner_andWritesSeen() = runTest(dispatcher) {
        val seen = FakeShowcaseSeenStore()
        val vm = viewModel(entries = emptyList(), documents = emptyList(), seenStore = seen)
        vm.state.first { it.scanShowcase }

        vm.onEvent(HomeEvent.ScanShowcaseActedOn)

        assertIs<HomeEffect.OpenScanner>(vm.effects.first())
        advanceUntilIdle()
        assertTrue(ShowcaseHookId.SCAN_BUTTON in seen.seen)
    }

    @Test
    fun scanShowcaseLeft_releasesWithoutSeen_soTheNextVisitShowsItAgain() = runTest(dispatcher) {
        val seen = FakeShowcaseSeenStore()
        val vm = viewModel(entries = emptyList(), documents = emptyList(), seenStore = seen)
        vm.state.first { it.scanShowcase }

        vm.onEvent(HomeEvent.ScanShowcaseLeft)

        assertTrue(seen.seen.isEmpty())
        // The same session's next visit re-requests and is granted again.
        assertTrue(vm.state.first { it.scanShowcase }.scanShowcase)
    }

    @Test
    fun healthShowcase_grantedOnTheFirstScoredDashboard() = runTest(dispatcher) {
        val vm = viewModel()

        assertTrue(vm.state.first { it.content is Loadable.Ready && it.healthShowcase }.healthShowcase)
    }

    @Test
    fun healthShowcase_notGranted_beforeAnythingIsScored() = runTest(dispatcher) {
        val vm = viewModel(entries = emptyList(), documents = emptyList())
        vm.state.first { it.content is Loadable.Ready }

        assertFalse(vm.state.value.healthShowcase)
    }

    @Test
    fun healthShowcaseActedOn_opensTheBreakdown_andWritesSeen() = runTest(dispatcher) {
        val seen = FakeShowcaseSeenStore()
        val vm = viewModel(seenStore = seen)
        vm.state.first { it.healthShowcase }

        vm.onEvent(HomeEvent.HealthShowcaseActedOn)

        assertIs<HomeEffect.OpenHealthScore>(vm.effects.first())
        advanceUntilIdle()
        assertTrue(ShowcaseHookId.HEALTH_SCORE_BREAKDOWN in seen.seen)
    }

    @Test
    fun proPlan_isCarriedForTheProCopyVariant() = runTest(dispatcher) {
        val vm = viewModel(isPro = true)

        assertTrue(vm.state.first { it.content is Loadable.Ready }.proPlan)
    }

    @Test
    fun autoOdometerTapped_opensTheEducationScreen() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.first { it.content is Loadable.Ready }
        vm.onEvent(HomeEvent.AutoOdometerTapped)
        assertIs<HomeEffect.OpenAutoOdometer>(vm.effects.first())
    }

    private fun viewModel(
        carId: CarId? = TEST_CAR,
        entries: List<ServiceLogEntry> = this.entries,
        documents: List<Document> = this.documents,
        logs: FakeServiceLogRepository = FakeServiceLogRepository(entries),
        analytics: RecordingAnalytics = RecordingAnalytics(),
        isPro: Boolean = false,
        detectedFillsUsed: Int = 0,
        bond: VehicleBond? = null,
        trackingEnabled: Boolean = false,
        autoOdometerEnabled: Boolean = true,
        refuelDetectEnabled: Boolean = true,
        seenStore: FakeShowcaseSeenStore = FakeShowcaseSeenStore(),
    ) = HomeViewModel(
        activeCar = FakeActiveCarProvider(carId),
        observeHome = ObserveHomeUseCase(
            cars = FakeCarRepository(),
            logs = logs,
            documents = FakeDocumentRepository(documents),
            scores = FakeHealthScoreRepository(),
            fills = FakeFuelFillRepository(),
            owners = FakeOwnerProfileRepository(),
            city = FakeCurrentCityProvider(),
            fuelPrices = FakeFuelPriceProvider(),
            currentOdometer = currentOdometerFrom(logs),
            clock = FixedClock(),
            timeZone = TimeZone.UTC,
        ),
        detection = FakeRefuelDetectionStore(),
        bonds = FakeVehicleBondStore(bond),
        tracker = FakeTripTracker(enabled = trackingEnabled),
        showcase = ShowcaseArbiter(seenStore),
        entitlements = FakeEntitlementSource(isPro = isPro),
        telemetry = telemetry(analytics),
        // Both flags on, which is what they default to. A test that wants either offer
        // hidden can now say so, which was impossible while these were compile-time consts.
        config = featureConfig(autoOdometerEnabled, refuelDetectEnabled),
    )

    private fun featureConfig(
        autoOdometer: Boolean = true,
        refuelDetect: Boolean = true,
    ) = object : FeatureConfig {
        override val autoOdometerEnabled = autoOdometer
        override val refuelDetectEnabled = refuelDetect
        override val challanEnabled = false
        override val plateLookupEnabled = false
        override val advisoryClassifierEnabled = false
    }

    private class FakeEntitlementSource(private val isPro: Boolean) : EntitlementSource {
        override fun observe(): Flow<Entitlements> =
            flowOf(Entitlements(plan = if (isPro) Plan.PRO else Plan.FREE))

        override suspend fun refresh() = Unit
    }

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

    private class FakeShowcaseSeenStore : ShowcaseSeenStore {
        val seen = mutableSetOf<ShowcaseHookId>()
        override suspend fun isSeen(hook: ShowcaseHookId): Boolean = hook in seen
        override suspend fun markSeen(hook: ShowcaseHookId) {
            seen += hook
        }

        override suspend fun clearAll() = seen.clear()
    }

    private class FakeVehicleBondStore(private val bond: VehicleBond?) : VehicleBondStore {
        override suspend fun bond(): VehicleBond? = bond
        override suspend fun saveBond(bond: VehicleBond) = Unit
        override suspend fun clearBond() = Unit
    }

    private class FakeTripTracker(enabled: Boolean) : TripTracker {
        private val enabledFlow = MutableStateFlow(enabled)
        override suspend fun setEnabled(enabled: Boolean) {
            enabledFlow.value = enabled
        }

        override suspend fun armFromPersistedState() = Unit
        override val isEnabled: Flow<Boolean> get() = enabledFlow
        override val status: Flow<TrackingStatus> get() = flowOf(TrackingStatus.Disabled)
        override suspend fun pauseActiveTrip() = Unit
        override suspend fun resumeActiveTrip() = Unit
        override suspend fun discardActiveTrip() = Unit
        override suspend fun startIfConnected() = Unit
    }
}
