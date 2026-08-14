package com.hopcape.odo.feature.healthscore.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.Entitlements
import com.hopcape.odo.core.domain.entitlement.Plan
import com.hopcape.odo.core.domain.entitlement.ProFeature
import com.hopcape.odo.core.domain.health.model.HealthBand
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.feature.healthscore.domain.usecase.FakeDocumentRepository
import com.hopcape.odo.feature.healthscore.domain.usecase.FakeHealthScoreRepository
import com.hopcape.odo.feature.healthscore.domain.usecase.FakeServiceLogRepository
import com.hopcape.odo.feature.healthscore.domain.usecase.FixedClock
import com.hopcape.odo.feature.healthscore.domain.usecase.ObserveHealthScoreUseCase
import com.hopcape.odo.feature.healthscore.domain.usecase.currentOdometerFrom
import com.hopcape.odo.feature.healthscore.domain.usecase.RecordHealthScoreUseCase
import com.hopcape.odo.feature.healthscore.domain.usecase.SequentialIds
import com.hopcape.odo.feature.healthscore.domain.usecase.TEST_CAR
import com.hopcape.odo.feature.healthscore.domain.usecase.TEST_OWNER
import com.hopcape.odo.feature.healthscore.domain.usecase.reading
import com.hopcape.odo.feature.healthscore.domain.usecase.testDocument
import com.hopcape.odo.feature.healthscore.domain.usecase.testEntry
import com.hopcape.odo.feature.healthscore.domain.usecase.testScore
import com.hopcape.odo.feature.healthscore.domain.usecase.testSnapshot
import com.hopcape.odo.feature.healthscore.presentation.state.Loadable
import com.hopcape.performance.api.APM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertTrue
import kotlin.time.Instant

class HealthScoreViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val now = Instant.parse("2026-08-01T10:00:00Z")

    /** A well-kept car: serviced twice this year, bills attached, papers in force. */
    private val entries = listOf(
        testEntry("log-1", LocalDate(2026, 1, 15), odometerKm = 36_000, verified = true),
        testEntry("log-2", LocalDate(2026, 6, 20), odometerKm = 43_000, verified = true),
    )
    private val readings = listOf(
        reading(LocalDate(2026, 1, 15), km = 36_000),
        reading(LocalDate(2026, 6, 20), km = 43_000),
    )
    private val documents = listOf(
        testDocument(DocumentType.INSURANCE, LocalDate(2027, 3, 31)),
        testDocument(DocumentType.PUC, LocalDate(2026, 12, 1)),
        testDocument(DocumentType.RC, expiresOn = null),
    )

    @Test
    fun showsTheScoreItsBandAndTheBreakdown() = runTest(dispatcher) {
        val content = viewModel().content()

        assertEquals(76, content.score)
        assertEquals(HealthBand.GOOD, content.band)
        assertEquals(HealthFactorKind.entries, content.factors.map { it.kind })
    }

    @Test
    fun theBiggestOpportunityIsTheFactorWithTheMostLeft() = runTest(dispatcher) {
        val content = viewModel().content()

        // Cost fairness is untouched at 0 of 20; history is missing 4 of 15.
        assertEquals(HealthFactorKind.COST_EFFICIENCY, content.opportunity?.kind)
        assertEquals(20, content.opportunity?.missing)
    }

    @Test
    fun withNoScoreFromAMonthAgo_thereIsNothingToSay() = runTest(dispatcher) {
        assertEquals(HealthNote.NoHistoryYet, viewModel().content().note)
    }

    @Test
    fun aMonthOldScoreBecomesTheDelta() = runTest(dispatcher) {
        val history = FakeHealthScoreRepository(
            mutableListOf(testSnapshot(score = testScore(maintenance = 25, documentation = 30, cost = 0, history = 11))),
        )

        val note = assertIs<HealthNote.Delta>(viewModel(history = history).content().note)
        assertEquals(10, note.points)
    }

    @Test
    fun aScoreThatHeldSteadyIsStillAnAnswer() = runTest(dispatcher) {
        val history = FakeHealthScoreRepository(
            mutableListOf(testSnapshot(score = testScore(maintenance = 35, documentation = 30, cost = 0, history = 11))),
        )

        assertEquals(HealthNote.Delta(0), viewModel(history = history).content().note)
    }

    @Test
    fun aCarWithNothingLoggedIsToldHowToStart() = runTest(dispatcher) {
        val content = viewModel(
            entries = emptyList(),
            documents = emptyList(),
            readings = listOf(reading(LocalDate(2026, 8, 1), km = 45_000)),
        ).content()

        // Zero because nothing is proven, not because the car is in bad shape — so the
        // screen offers a way forward instead of "0 points this month".
        assertEquals(0, content.score)
        assertEquals(HealthNote.NothingLoggedYet, content.note)
        assertTrue(content.hasNothingLogged)
    }

    @Test
    fun withNoCarYet_theScreenKeepsWaiting() = runTest(dispatcher) {
        val viewModel = viewModel(carId = null)
        advanceUntilIdle()

        assertIs<Loadable.Loading>(viewModel.state.value.content)
    }

    @Test
    fun theScoreIsKeptWheneverItMoves() = runTest(dispatcher) {
        val history = FakeHealthScoreRepository()

        viewModel(history = history).content()
        advanceUntilIdle()

        assertEquals(1, history.recorded.size)
        assertEquals(76, history.recorded.single().score.total)
        assertEquals(TEST_OWNER, history.recorded.single().ownerId)
        assertEquals(now, history.recorded.single().computedAt)
    }

    @Test
    fun anUnchangedScoreIsNotStoredTwice() = runTest(dispatcher) {
        val logs = FakeServiceLogRepository(entries, readings)
        val history = FakeHealthScoreRepository()
        val viewModel = viewModel(logs = logs, history = history)
        viewModel.content()
        advanceUntilIdle()

        // A re-emission that changes nothing about the score — the same data again.
        logs.emit(entries, readings)
        advanceUntilIdle()

        assertEquals(1, history.recorded.size)
    }

    @Test
    fun aFailedHistoryWriteNeverReachesTheScreen() = runTest(dispatcher) {
        val history = FakeHealthScoreRepository(failing = true)

        val content = viewModel(history = history).content()

        assertEquals(76, content.score)
    }

    @Test
    fun freeOwnersGetTheLockedBreakdown() = runTest(dispatcher) {
        assertEquals(false, viewModel(isPro = false).content().entitlements.has(ProFeature.HEALTH_BREAKDOWN))
        assertTrue(viewModel(isPro = true).content().entitlements.has(ProFeature.HEALTH_BREAKDOWN))
    }

    @Test
    fun tappingInfoOpensTheExplainer() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.content()

        viewModel.onEvent(HealthScoreEvent.InfoTapped)

        assertEquals(HealthScoreEffect.OpenInfo, viewModel.effects.first())
    }

    @Test
    fun tappingUnlockOpensThePaywall() = runTest(dispatcher) {
        val viewModel = viewModel(isPro = false)
        viewModel.content()

        viewModel.onEvent(HealthScoreEvent.UnlockTapped)

        assertEquals(HealthScoreEffect.OpenPaywall, viewModel.effects.first())
    }

    @Test
    fun opensAreReportedOnceWithTheBand() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)
        viewModel.content()
        advanceUntilIdle()

        val opened = analytics.events.filter { it.first == HealthScoreTelemetry.Event.SCORE_OPENED }
        assertEquals(1, opened.size)
        assertEquals(76, opened.single().second[HealthScoreTelemetry.Key.SCORE])
        assertEquals(HealthBand.GOOD.name, opened.single().second[HealthScoreTelemetry.Key.BAND])
        assertEquals(true, opened.single().second[HealthScoreTelemetry.Key.IS_PRO])
        assertEquals(false, opened.single().second[HealthScoreTelemetry.Key.NOTHING_LOGGED])
    }

    @Test
    fun tappingUnlockIsReportedWithTheScoreItSoldAgainst() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(isPro = false, analytics = analytics)
        viewModel.content()

        viewModel.onEvent(HealthScoreEvent.UnlockTapped)
        advanceUntilIdle()

        val tapped = analytics.events.single { it.first == HealthScoreTelemetry.Event.UNLOCK_TAPPED }
        assertEquals(76, tapped.second[HealthScoreTelemetry.Key.SCORE])
    }

    @Test
    fun goingBackReportsNothing() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)
        viewModel.content()
        val before = analytics.events.size

        viewModel.onEvent(HealthScoreEvent.BackTapped)
        advanceUntilIdle()

        // Leaving a screen is not an outcome worth a dashboard row.
        assertEquals(before, analytics.events.size)
        assertEquals(HealthScoreEffect.GoBack, viewModel.effects.first())
    }

    /* ------------------------- fixtures ------------------------- */

    private fun viewModel(
        carId: CarId? = TEST_CAR,
        entries: List<ServiceLogEntry> = this.entries,
        readings: List<OdometerReading> = this.readings,
        documents: List<Document> = this.documents,
        logs: FakeServiceLogRepository = FakeServiceLogRepository(entries, readings),
        history: FakeHealthScoreRepository = FakeHealthScoreRepository(),
        isPro: Boolean = true,
        analytics: RecordingAnalytics = RecordingAnalytics(),
    ) = HealthScoreViewModel(
        activeCar = FakeActiveCarProvider(carId),
        observeHealthScore = ObserveHealthScoreUseCase(
            logs = logs,
            documents = FakeDocumentRepository(documents),
            snapshots = history,
            entitlements = entitlementsOf(isPro),
            currentOdometer = currentOdometerFrom(logs),
            clock = FixedClock(now),
            timeZone = TimeZone.UTC,
        ),
        recordHealthScore = RecordHealthScoreUseCase(
            snapshots = history,
            owners = CurrentOwnerProvider { TEST_OWNER },
            ids = SequentialIds(),
            clock = FixedClock(now),
        ),
        telemetry = HealthScoreTelemetry(
            logger = HLogger.asLogger(),
            analytics = analytics,
            tracer = APM.asTracer(),
            ids = FixedIdGenerator(),
        ),
    )

    private suspend fun HealthScoreViewModel.content(): HealthScoreContent =
        assertIs<Loadable.Ready<HealthScoreContent>>(state.first { it.content is Loadable.Ready }.content).value

    private class FakeActiveCarProvider(carId: CarId?) : ActiveCarProvider {
        private val state = MutableStateFlow(carId)
        override val activeCarId: StateFlow<CarId?> = state
    }

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

/** An [EntitlementSource] that stands still on one plan. */
private fun entitlementsOf(isPro: Boolean) = object : EntitlementSource {
    override fun observe() = flowOf(Entitlements(if (isPro) Plan.PRO else Plan.FREE))
    override suspend fun refresh() = Unit
}
