package com.hopcape.odo.feature.costtracker.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.cost.fuel.FuelPrice
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceSource
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.feature.costtracker.domain.usecase.ClearFuelRateUseCase
import com.hopcape.odo.feature.costtracker.domain.usecase.FakeCarRepository
import com.hopcape.odo.feature.costtracker.domain.usecase.FakeFuelPriceOverrides
import com.hopcape.odo.feature.costtracker.domain.usecase.FakeFuelPriceProvider
import com.hopcape.odo.feature.costtracker.domain.usecase.FixedClock
import com.hopcape.odo.feature.costtracker.domain.usecase.GetFuelRateUseCase
import com.hopcape.odo.feature.costtracker.domain.usecase.SetFuelRateUseCase
import com.hopcape.odo.feature.costtracker.domain.usecase.TEST_CAR
import com.hopcape.odo.feature.costtracker.domain.usecase.cityProvider
import com.hopcape.odo.feature.costtracker.domain.usecase.testCar
import com.hopcape.odo.feature.costtracker.domain.usecase.testFuelPrice
import com.hopcape.odo.feature.costtracker.presentation.fuelrate.FuelRateEffect
import com.hopcape.odo.feature.costtracker.presentation.fuelrate.FuelRateEvent
import com.hopcape.odo.feature.costtracker.presentation.fuelrate.FuelRateViewModel
import com.hopcape.odo.feature.costtracker.presentation.fuelrate.toPaise
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class FuelRateViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun opensOnThePriceInForce() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        // ₹105.00 a litre, prefilled so a correction is an edit rather than a re-entry.
        assertEquals("105", viewModel.state.value.price)
        assertEquals(FuelUnit.LITRE, viewModel.state.value.unit)
        // Odo's own figure is showing, so there is nothing of theirs to drop.
        assertFalse(viewModel.state.value.canClear)
    }

    @Test
    fun aRateTheyAlreadySetCanBeDropped() = runTest(dispatcher) {
        val viewModel = viewModel(
            price = testFuelPrice(pricePaise = 11_040, city = null, source = FuelPriceSource.OWNER),
        )
        advanceUntilIdle()

        assertEquals("110.40", viewModel.state.value.price)
        assertTrue(viewModel.state.value.canClear)
    }

    @Test
    fun savingStoresPaiseAndClosesTheSheet() = runTest(dispatcher) {
        val overrides = FakeFuelPriceOverrides()
        val viewModel = viewModel(overrides = overrides)
        advanceUntilIdle()

        viewModel.onEvent(FuelRateEvent.PriceChanged("104.40"))
        viewModel.onEvent(FuelRateEvent.SaveTapped)
        advanceUntilIdle()

        val (fuelType, amount, on) = overrides.set.single()
        assertEquals(FuelType.PETROL, fuelType)
        assertEquals(10_440L, amount.paise)
        assertEquals(LocalDate(2026, 8, 1), on)
        assertEquals(FuelRateEffect.Dismiss, viewModel.effects.first())
    }

    @Test
    fun aPriceOutOfRangeIsRefusedWithAMessageAndNothingIsStored() = runTest(dispatcher) {
        val overrides = FakeFuelPriceOverrides()
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(overrides = overrides, analytics = analytics)
        advanceUntilIdle()

        viewModel.onEvent(FuelRateEvent.PriceChanged("10440"))
        viewModel.onEvent(FuelRateEvent.SaveTapped)
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.saving, "the sheet has to be usable again")
        assertTrue(overrides.set.isEmpty())
        assertTrue(analytics.events.any { it.first == CostTrackerTelemetry.Event.FUEL_RATE_REFUSED })
    }

    @Test
    fun textThatIsNotAPriceIsRefusedTheSameWay() = runTest(dispatcher) {
        val overrides = FakeFuelPriceOverrides()
        val viewModel = viewModel(overrides = overrides)
        advanceUntilIdle()

        viewModel.onEvent(FuelRateEvent.PriceChanged("abc"))
        viewModel.onEvent(FuelRateEvent.SaveTapped)
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertTrue(overrides.set.isEmpty())
    }

    @Test
    fun clearingDropsTheirRateAndClosesTheSheet() = runTest(dispatcher) {
        val overrides = FakeFuelPriceOverrides()
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(
            price = testFuelPrice(city = null, source = FuelPriceSource.OWNER),
            overrides = overrides,
            analytics = analytics,
        )
        advanceUntilIdle()

        viewModel.onEvent(FuelRateEvent.ClearTapped)
        advanceUntilIdle()

        assertEquals(listOf(FuelType.PETROL), overrides.cleared)
        assertEquals(FuelRateEffect.Dismiss, viewModel.effects.first())
        assertTrue(analytics.events.any { it.first == CostTrackerTelemetry.Event.FUEL_RATE_CLEARED })
    }

    @Test
    fun aFailedWriteKeepsTheSheetOpenWithAMessage() = runTest(dispatcher) {
        val viewModel = viewModel(overrides = FakeFuelPriceOverrides(failing = true))
        advanceUntilIdle()

        viewModel.onEvent(FuelRateEvent.PriceChanged("104.40"))
        viewModel.onEvent(FuelRateEvent.SaveTapped)
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.saving)
    }

    @Test
    fun rupeesTypedBecomePaise() {
        assertEquals(10_440L, toPaise("104.40"))
        assertEquals(10_440L, toPaise("104.4"))
        assertEquals(10_400L, toPaise("104"))
        assertEquals(10_440L, toPaise(" 104.40 "))
        assertEquals(110_440L, toPaise("1,104.40"))
    }

    @Test
    fun anythingThatIsNotAPriceReadsAsNothing() {
        assertNull(toPaise(""))
        assertNull(toPaise("abc"))
        assertNull(toPaise("104.405"))
        assertNull(toPaise("104.4.4"))
        assertNull(toPaise("-104"))
    }

    /* ------------------------- fixtures ------------------------- */

    private fun viewModel(
        price: FuelPrice? = testFuelPrice(),
        overrides: FakeFuelPriceOverrides = FakeFuelPriceOverrides(),
        analytics: RecordingAnalytics = RecordingAnalytics(),
        carId: CarId? = TEST_CAR,
    ) = FuelRateViewModel(
        activeCar = FakeActiveCarProvider(carId),
        getFuelRate = GetFuelRateUseCase(
            cars = FakeCarRepository(testCar(FuelType.PETROL)),
            city = cityProvider("Pune"),
            fuelPrices = FakeFuelPriceProvider(price),
        ),
        setFuelRate = SetFuelRateUseCase(
            overrides = overrides,
            clock = FixedClock(Instant.parse("2026-08-01T09:00:00Z")),
            timeZone = kotlinx.datetime.TimeZone.UTC,
        ),
        clearFuelRate = ClearFuelRateUseCase(overrides),
        telemetry = CostTrackerTelemetry(
            logger = HLogger.asLogger(),
            analytics = analytics,
            tracer = APM.asTracer(),
            ids = FixedIdGenerator(),
        ),
    )

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
