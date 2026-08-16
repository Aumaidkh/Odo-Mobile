package com.hopcape.odo.feature.timeline.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.feature.timeline.FakeActiveCarProvider
import com.hopcape.odo.feature.timeline.FakeCarRepository
import com.hopcape.odo.feature.timeline.FakeDocumentRepository
import com.hopcape.odo.feature.timeline.FakeFuelFillRepository
import com.hopcape.odo.feature.timeline.FakeHealthScoreRepository
import com.hopcape.odo.feature.timeline.FakeServiceLogRepository
import com.hopcape.odo.feature.timeline.TEST_CAR
import com.hopcape.odo.feature.timeline.domain.model.ActivityCategory
import com.hopcape.odo.feature.timeline.domain.model.TimelineFilter
import com.hopcape.odo.feature.timeline.domain.usecase.ObserveTimelineUseCase
import com.hopcape.odo.feature.timeline.presentation.sheets.TimelineFilterViewModel
import com.hopcape.odo.feature.timeline.presentation.state.Loadable
import com.hopcape.odo.feature.timeline.testDocument
import com.hopcape.odo.feature.timeline.testEntry
import com.hopcape.odo.feature.timeline.testSnapshot
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
import kotlin.test.assertTrue

class TimelineViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** A car with a history: two services, two papers, and a score that moved. */
    private val entries = listOf(
        testEntry("l1", LocalDate(2026, 7, 12), verified = true),
        testEntry("l2", LocalDate(2026, 6, 21)),
    )
    private val documents = listOf(
        testDocument(DocumentType.PUC, addedOn = LocalDate(2026, 6, 2)),
        testDocument(DocumentType.INSURANCE, addedOn = LocalDate(2026, 6, 1)),
    )
    private val scores = listOf(
        testSnapshot("before", "2026-07-06T10:00:00Z", total = 70),
        testSnapshot("after", "2026-07-07T10:00:00Z", total = 74),
    )

    @Test
    fun showsTheWholeFeedNewestFirst() = runTest(dispatcher) {
        val content = viewModel().content()

        assertEquals("Swift VXI", content.carName)
        assertEquals(6, content.events.size)
        assertEquals(6, content.totalEvents)
        assertEquals(false, content.isFiltered)
        assertEquals(LocalDate(2026, 7, 12), content.events.first().date)
    }

    @Test
    fun withNoCarYet_theTabKeepsWaiting() = runTest(dispatcher) {
        val viewModel = viewModel(carId = null)
        advanceUntilIdle()

        assertIs<Loadable.Loading>(viewModel.state.value.content)
    }

    @Test
    fun aCarWithNoHistoryIsOfferedTheFirstScan() = runTest(dispatcher) {
        val content = viewModel(entries = emptyList(), documents = emptyList(), scores = emptyList()).content()

        // The milestone alone is not a history.
        assertEquals(1, content.events.size)
        assertTrue(content.isNewUser)
    }

    @Test
    fun theFilterNarrowsTheFeedWithoutRewritingTheRecord() = runTest(dispatcher) {
        val filters = TimelineFilterStore()
        val viewModel = viewModel(filters = filters)
        viewModel.content()

        filters.update { it.withCategory(ActivityCategory.DOCUMENTS, selected = false) }
        advanceUntilIdle()

        val content = viewModel.state.value.content
        val value = assertIs<Loadable.Ready<TimelineContent>>(content).value
        assertEquals(4, value.events.size)
        // The header still counts the car's whole record.
        assertEquals(6, value.totalEvents)
        assertTrue(value.isFiltered)
        assertTrue(value.events.none { it is ActivityEvent.DocumentFiled })
    }

    @Test
    fun aFilterThatHidesEverythingIsNotANewUser() = runTest(dispatcher) {
        val filters = TimelineFilterStore()
        filters.update { TimelineFilter(categories = emptySet()) }
        val content = viewModel(filters = filters).content()

        assertTrue(content.isFilteredEmpty)
        assertEquals(false, content.isNewUser)
    }

    @Test
    fun theFeedFollowsTheLogAsItChanges() = runTest(dispatcher) {
        val logs = FakeServiceLogRepository(entries)
        val viewModel = viewModel(logs = logs)
        assertEquals(6, viewModel.content().events.size)

        logs.emit(entries + testEntry("l3", LocalDate(2026, 7, 30)))
        advanceUntilIdle()

        assertEquals(7, assertIs<Loadable.Ready<TimelineContent>>(viewModel.state.value.content).value.events.size)
    }

    @Test
    fun tappingAServiceOpensItForTheActiveCar() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.content()

        viewModel.onEvent(TimelineEvent.ServiceTapped(ServiceLogId("l1")))

        assertEquals(
            TimelineEffect.OpenService(logId = "l1", carId = TEST_CAR.value),
            viewModel.effects.first(),
        )
    }

    @Test
    fun tappingShareSharesTheActiveCarsRecord() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.content()

        viewModel.onEvent(TimelineEvent.ShareTapped)

        assertEquals(TimelineEffect.ShareRecord(carId = TEST_CAR.value), viewModel.effects.first())
    }

    @Test
    fun addBillAndScanFirstBothReachTheScanner() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.content()

        viewModel.onEvent(TimelineEvent.AddBillTapped(ServiceLogId("l2")))
        assertEquals(TimelineEffect.OpenScanner, viewModel.effects.first())

        viewModel.onEvent(TimelineEvent.ScanFirstTapped)
        assertEquals(TimelineEffect.OpenScanner, viewModel.effects.first())
    }

    @Test
    fun opensAreReportedOnceWithTheShapeOfTheRecord() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)
        viewModel.content()
        advanceUntilIdle()

        val opened = analytics.events.filter { it.first == TimelineTelemetry.Event.OPENED }
        assertEquals(1, opened.size)
        assertEquals(6, opened.single().second[TimelineTelemetry.Key.EVENT_COUNT])
        assertEquals(true, opened.single().second[TimelineTelemetry.Key.HAS_SERVICES])
        assertEquals(false, opened.single().second[TimelineTelemetry.Key.IS_NEW_USER])
    }

    @Test
    fun addBillIsReportedBecauseItFeedsTheNorthStar() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)
        viewModel.content()

        viewModel.onEvent(TimelineEvent.AddBillTapped(ServiceLogId("l2")))
        advanceUntilIdle()

        assertEquals(1, analytics.events.count { it.first == TimelineTelemetry.Event.ADD_BILL_TAPPED })
    }

    /* ------------------------- the filter sheet ------------------------- */

    @Test
    fun theSheetCountsWhatExistsNotWhatIsShowing() = runTest(dispatcher) {
        val filters = TimelineFilterStore()
        filters.update { it.withCategory(ActivityCategory.DOCUMENTS, selected = false) }
        val sheet = filterViewModel(filters = filters)
        val state = sheet.state.first { it.counts.isNotEmpty() }

        assertEquals(2, state.countOf(ActivityCategory.DOCUMENTS))
        assertEquals(2, state.countOf(ActivityCategory.SERVICES))
        // …while the button offers only what turning nothing else on would show.
        assertEquals(4, state.shownCount)
    }

    @Test
    fun tickingARowNarrowsTheFeedBehindTheSheet() = runTest(dispatcher) {
        val filters = TimelineFilterStore()
        val tab = viewModel(filters = filters)
        val sheet = filterViewModel(filters = filters)
        tab.content()

        sheet.onCategoryToggled(ActivityCategory.SERVICES, selected = false)
        advanceUntilIdle()

        val content = assertIs<Loadable.Ready<TimelineContent>>(tab.state.value.content).value
        assertTrue(content.events.none { it is ActivityEvent.Service })
    }

    @Test
    fun theFilterIsReportedAsItStandsAfterTheTapNotBefore() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val sheet = filterViewModel(analytics = analytics)

        sheet.onOnlyFlaggedToggled(true)
        advanceUntilIdle()

        val applied = analytics.events.single { it.first == TimelineTelemetry.Event.FILTER_APPLIED }
        assertEquals(true, applied.second[TimelineTelemetry.Key.ONLY_FLAGGED])
    }

    /* ------------------------- fixtures ------------------------- */

    private fun viewModel(
        carId: CarId? = TEST_CAR,
        entries: List<ServiceLogEntry> = this.entries,
        documents: List<Document> = this.documents,
        scores: List<HealthSnapshot> = this.scores,
        logs: FakeServiceLogRepository = FakeServiceLogRepository(entries),
        filters: TimelineFilterStore = TimelineFilterStore(),
        analytics: RecordingAnalytics = RecordingAnalytics(),
    ) = TimelineViewModel(
        activeCar = FakeActiveCarProvider(carId),
        observeTimeline = ObserveTimelineUseCase(
            cars = FakeCarRepository(),
            logs = logs,
            documents = FakeDocumentRepository(documents),
            scores = FakeHealthScoreRepository(scores),
            fills = FakeFuelFillRepository(),
            timeZone = TimeZone.UTC,
        ),
        filters = filters,
        telemetry = telemetry(analytics),
    )

    private fun filterViewModel(
        filters: TimelineFilterStore = TimelineFilterStore(),
        analytics: RecordingAnalytics = RecordingAnalytics(),
    ) = TimelineFilterViewModel(
        activeCar = FakeActiveCarProvider(TEST_CAR),
        observeTimeline = ObserveTimelineUseCase(
            cars = FakeCarRepository(),
            logs = FakeServiceLogRepository(entries),
            documents = FakeDocumentRepository(documents),
            scores = FakeHealthScoreRepository(scores),
            fills = FakeFuelFillRepository(),
            timeZone = TimeZone.UTC,
        ),
        filters = filters,
        telemetry = telemetry(analytics),
    )

    private fun telemetry(analytics: RecordingAnalytics) = TimelineTelemetry(
        logger = HLogger.asLogger(),
        analytics = analytics,
        tracer = APM.asTracer(),
        ids = FixedIdGenerator(),
    )

    private suspend fun TimelineViewModel.content(): TimelineContent =
        assertIs<Loadable.Ready<TimelineContent>>(state.first { it.content is Loadable.Ready }.content).value

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
