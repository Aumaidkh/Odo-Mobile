package com.hopcape.odo.feature.fairnesscheck.presentation.report

import arrow.core.getOrElse
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.fairness.analysis.FairnessAnalyzer
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
import com.hopcape.odo.core.domain.fairness.model.FairnessQueryItem
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.performance.api.APM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FairnessViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aBenchmarkedBill_becomesAReport() = runTest(dispatcher) {
        val viewModel = viewModel()

        val report = viewModel.report()

        assertEquals("Pune", report.city)
        assertIs<FairnessVerdictUiState.Over>(report.verdict)
        assertEquals(1, report.lines.size)
    }

    @Test
    fun withNoCityOnTheProfile_theScreenAsksForOneRatherThanGuessing() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(city = null, analytics = analytics)

        assertEquals(
            FairnessUiState.Content.NoCity,
            viewModel.state.first { it.content != FairnessUiState.Content.Loading }.content,
        )
        assertTrue(analytics.events.any { it.first == FairnessTelemetry.Event.NO_CITY })
    }

    @Test
    fun aThrownLookup_offersARetryRatherThanAnEmptyReport() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analyzer = ThrowingAnalyzer, analytics = analytics)

        val content = viewModel.state.first { it.content != FairnessUiState.Content.Loading }.content

        assertIs<FairnessUiState.Content.Failed>(content)
        val failure = analytics.events.single { it.first == FairnessTelemetry.Event.FAILED }
        assertEquals("IllegalStateException", failure.second[FairnessTelemetry.Key.REASON])
    }

    @Test
    fun retry_runsTheCheckAgain() = runTest(dispatcher) {
        val analyzer = FailingOnceAnalyzer()
        val viewModel = viewModel(analyzer = analyzer)
        assertIs<FairnessUiState.Content.Failed>(
            viewModel.state.first { it.content != FairnessUiState.Content.Loading }.content,
        )

        viewModel.onEvent(FairnessEvent.RetryTapped)

        assertIs<FairnessUiState.Content.Report>(
            viewModel.state.first { it.content is FairnessUiState.Content.Report }.content,
        )
    }

    @Test
    fun theCheckedEvent_carriesHowMuchOfTheBillCouldBePriced() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        viewModel(
            items = listOf(
                item("Front pads", ServiceCategory.BRAKES, 330_000),
                item("Sundries", null, 50_000),
            ),
            analytics = analytics,
        ).report()

        val checked = analytics.events.single { it.first == FairnessTelemetry.Event.CHECKED }
        assertEquals(2, checked.second[FairnessTelemetry.Key.LINE_COUNT])
        assertEquals(1, checked.second[FairnessTelemetry.Key.BENCHMARKED_LINES])
        assertEquals("Over", checked.second[FairnessTelemetry.Key.OUTCOME])
    }

    @Test
    fun reporting_needsTheEntryItIsAbout() = runTest(dispatcher) {
        val viewModel = viewModel(logId = null, carId = null)
        viewModel.report()

        viewModel.onEvent(FairnessEvent.ReportTapped)

        // Nothing to file against, so nothing is emitted — the button is not offered either.
        assertEquals(false, viewModel.state.value.let { (it.content as FairnessUiState.Content.Report).canReport })
    }

    @Test
    fun reporting_handsTheEntryToTheOverchargeForm() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.report()

        viewModel.onEvent(FairnessEvent.ReportTapped)

        val effect = assertIs<FairnessEffect.OpenReportOvercharge>(viewModel.effects.first())
        assertEquals("log-1", effect.logId)
        assertEquals("car-1", effect.carId)
    }

    @Test
    fun setCity_opensTheProfile() = runTest(dispatcher) {
        val viewModel = viewModel(city = null)

        viewModel.onEvent(FairnessEvent.SetCityTapped)

        assertEquals(FairnessEffect.OpenProfile, viewModel.effects.first())
    }

    /* ------------------------- fixtures ------------------------- */

    private suspend fun FairnessViewModel.report(): FairnessUiState.Content.Report =
        assertIs<FairnessUiState.Content.Report>(state.first { it.content is FairnessUiState.Content.Report }.content)

    private fun viewModel(
        items: List<FairnessQueryItem> = listOf(item("Front pads", ServiceCategory.BRAKES, 330_000)),
        logId: String? = "log-1",
        carId: String? = "car-1",
        city: String? = "Pune",
        analyzer: FairnessAnalyzer = StubAnalyzer,
        analytics: RecordingAnalytics = RecordingAnalytics(),
    ) = FairnessViewModel(
        input = FairnessCheckInput(items = items, logId = logId, carId = carId),
        analyzer = analyzer,
        city = CurrentCityProvider { city },
        telemetry = FairnessTelemetry(
            logger = HLogger.asLogger(),
            analytics = analytics,
            tracer = APM.asTracer(),
            ids = FixedIdGenerator(),
        ),
    )

    private fun item(label: String, category: ServiceCategory?, paise: Long) =
        FairnessQueryItem(label = label, category = category, amount = paise(paise))

    /** Benchmarks brakes at Rs. 2,400 and knows nothing else — like the real pool. */
    private object StubAnalyzer : FairnessAnalyzer {
        override suspend fun analyze(query: FairnessQuery): FairnessReport = FairnessReport.of(
            query,
            mapOf(
                ServiceCategory.BRAKES to FairnessEstimate(
                    category = ServiceCategory.BRAKES,
                    city = query.city,
                    cityAverage = paise(240_000),
                    sampleSize = 24,
                ),
            ),
        )
    }

    private object ThrowingAnalyzer : FairnessAnalyzer {
        override suspend fun analyze(query: FairnessQuery): FairnessReport = error("boom")
    }

    private class FailingOnceAnalyzer : FairnessAnalyzer {
        private var calls = 0
        override suspend fun analyze(query: FairnessQuery): FairnessReport {
            calls++
            if (calls == 1) error("boom")
            return StubAnalyzer.analyze(query)
        }
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

private fun paise(value: Long): Amount = Amount.of(value).getOrElse { Amount.ZERO }
