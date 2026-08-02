package com.hopcape.odo.feature.costtracker.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceSource
import com.hopcape.odo.core.domain.cost.model.SpendCategory
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.feature.costtracker.domain.model.CostPeriod
import com.hopcape.odo.feature.costtracker.domain.usecase.FakeCarRepository
import com.hopcape.odo.feature.costtracker.domain.usecase.FakeFuelPriceProvider
import com.hopcape.odo.feature.costtracker.domain.usecase.FakeServiceLogRepository
import com.hopcape.odo.feature.costtracker.domain.usecase.FakeSettingsRepository
import com.hopcape.odo.feature.costtracker.domain.usecase.FixedClock
import com.hopcape.odo.feature.costtracker.domain.usecase.ObserveRunningCostUseCase
import com.hopcape.odo.feature.costtracker.domain.usecase.TEST_CAR
import com.hopcape.odo.feature.costtracker.domain.usecase.cityProvider
import com.hopcape.odo.feature.costtracker.domain.usecase.reading
import com.hopcape.odo.feature.costtracker.domain.usecase.testCar
import com.hopcape.odo.feature.costtracker.domain.usecase.testEntry
import com.hopcape.odo.feature.costtracker.domain.usecase.testFuelPrice
import com.hopcape.odo.feature.costtracker.presentation.runningcost.CostHeadline
import com.hopcape.odo.feature.costtracker.presentation.runningcost.FuelNote
import com.hopcape.odo.feature.costtracker.presentation.runningcost.RunningCostContent
import com.hopcape.odo.feature.costtracker.presentation.runningcost.RunningCostEvent
import com.hopcape.odo.feature.costtracker.presentation.runningcost.RunningCostViewModel
import com.hopcape.odo.feature.costtracker.presentation.state.Loadable
import com.hopcape.performance.api.APM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import kotlin.time.Instant

class RunningCostViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** 12,000 km over the year, one Rs. 8,000 service in it. */
    private val readings = listOf(
        reading(LocalDate(2025, 7, 15), km = 30_000),
        reading(LocalDate(2026, 6, 10), km = 42_000),
    )
    private val entries = listOf(
        testEntry("log-1", date = LocalDate(2026, 6, 10), odometerKm = 42_000, paise = 800_000),
    )

    @Test
    fun showsTheRateWithItsFuelNote() = runTest(dispatcher) {
        val content = viewModel().content()

        val headline = assertIs<CostHeadline.Rate>(content.headline)
        assertEquals(767L, headline.perKm.paise)
        assertEquals(12_000, content.distance.km)
        // Fuel is estimated, so the screen has to say what it was estimated from.
        val note = assertIs<FuelNote.Estimated>(content.fuelNote)
        assertEquals(10_500L, note.pricePerUnit.paise)
        assertEquals("pune", note.city)
        assertEquals(false, note.ownersOwn)
    }

    @Test
    fun aYearIsSixBarsWithThePeakMarked() = runTest(dispatcher) {
        val content = viewModel().content()

        assertEquals(6, content.spendBars.size)
        assertEquals(1, content.spendBars.count { it.highlighted })
        assertEquals(content.spendBars.maxOf { it.amount.paise }, content.spendBars.single { it.highlighted }.amount.paise)
    }

    @Test
    fun categoriesCarryTheirPerKmShare() = runTest(dispatcher) {
        val content = viewModel().content()

        assertEquals(
            listOf(SpendCategory.FUEL, SpendCategory.SERVICE),
            content.categories.map { it.category },
        )
        assertEquals(700L, content.categories.first().perKm?.paise)
    }

    @Test
    fun tooLittleDrivingShowsTheReasonInsteadOfARate() = runTest(dispatcher) {
        val content = viewModel(
            readings = listOf(reading(LocalDate(2026, 7, 1), km = 30_000)),
            entries = emptyList(),
        ).content()

        assertIs<CostHeadline.NotEnoughYet>(content.headline)
    }

    @Test
    fun withNoFuelPrice_theNoteSaysSo() = runTest(dispatcher) {
        val content = viewModel(price = null).content()

        assertEquals(FuelNote.Missing, content.fuelNote)
        assertEquals(listOf(SpendCategory.SERVICE), content.categories.map { it.category })
    }

    @Test
    fun theOwnersOwnRateIsCalledOut() = runTest(dispatcher) {
        val content = viewModel(
            price = testFuelPrice(pricePaise = 11_000, city = null, source = FuelPriceSource.OWNER),
        ).content()

        val note = assertIs<FuelNote.Estimated>(content.fuelNote)
        assertTrue(note.ownersOwn)
        assertEquals(null, note.city)
    }

    @Test
    fun choosingAPeriodRecomputesTheFigures() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.content()

        viewModel.onEvent(RunningCostEvent.PeriodSelected(CostPeriod.M3))
        advanceUntilIdle()

        assertEquals(CostPeriod.M3, viewModel.state.value.period)
        val content = viewModel.content()
        assertEquals(3, content.spendBars.size, "three months are charted one month at a time")
        // The quarter still has a rate: the distance since the last reading before it counts
        // against it, which is the only honest place to put kilometres nobody dated.
        assertIs<CostHeadline.Rate>(content.headline)
    }

    @Test
    fun withNoCarYet_theScreenKeepsWaiting() = runTest(dispatcher) {
        val viewModel = viewModel(carId = null)
        advanceUntilIdle()

        assertIs<Loadable.Loading>(viewModel.state.value.content)
    }

    @Test
    fun opensAreReportedOnceWithWhetherItCouldAnswer() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)
        viewModel.content()
        advanceUntilIdle()

        val opened = analytics.events.filter { it.first == CostTrackerTelemetry.Event.COST_OPENED }
        assertEquals(1, opened.size)
        assertEquals(true, opened.single().second[CostTrackerTelemetry.Key.HAS_RATE])
        assertEquals(true, opened.single().second[CostTrackerTelemetry.Key.FUEL_ESTIMATED])
        assertEquals(12_000, opened.single().second[CostTrackerTelemetry.Key.KM_DRIVEN])
    }

    @Test
    fun changingThePeriodIsReported() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)
        viewModel.content()

        viewModel.onEvent(RunningCostEvent.PeriodSelected(CostPeriod.M6))
        advanceUntilIdle()

        val changed = analytics.events.single { it.first == CostTrackerTelemetry.Event.PERIOD_CHANGED }
        assertEquals(CostPeriod.M6.name, changed.second[CostTrackerTelemetry.Key.PERIOD])
    }

    /* ------------------------- fixtures ------------------------- */

    private fun viewModel(
        carId: CarId? = TEST_CAR,
        readings: List<OdometerReading> = this.readings,
        entries: List<ServiceLogEntry> = this.entries,
        price: com.hopcape.odo.core.domain.cost.fuel.FuelPrice? = testFuelPrice(),
        analytics: RecordingAnalytics = RecordingAnalytics(),
        settings: AppSettings = AppSettings.Default,
    ) = RunningCostViewModel(
        activeCar = FakeActiveCarProvider(carId),
        settings = FakeSettingsRepository(settings),
        observeRunningCost = ObserveRunningCostUseCase(
            cars = FakeCarRepository(testCar(FuelType.PETROL)),
            logs = FakeServiceLogRepository(entries = entries, readings = readings),
            city = cityProvider("Pune"),
            fuelPrices = FakeFuelPriceProvider(price),
            clock = FixedClock(Instant.parse("2026-08-01T09:00:00Z")),
            timeZone = TimeZone.UTC,
        ),
        telemetry = CostTrackerTelemetry(
            logger = HLogger.asLogger(),
            analytics = analytics,
            tracer = APM.asTracer(),
            ids = FixedIdGenerator(),
        ),
    )

    private suspend fun RunningCostViewModel.content(): RunningCostContent =
        assertIs<Loadable.Ready<RunningCostContent>>(state.first { it.content is Loadable.Ready }.content).value

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
