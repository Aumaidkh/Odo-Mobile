package com.hopcape.odo.feature.costtracker.domain.usecase

import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.cost.model.CostShortfall
import com.hopcape.odo.core.domain.cost.model.SpendCategory
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.feature.costtracker.domain.model.CostPeriod
import com.hopcape.odo.feature.costtracker.domain.model.RunningCostSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ObserveRunningCostUseCaseTest {

    private val today = LocalDate(2026, 8, 1)

    /** 30,000 km before the year began, 42,000 by June — 12,000 km over the window. */
    private val readings = listOf(
        reading(LocalDate(2025, 7, 15), km = 30_000),
        reading(LocalDate(2026, 6, 10), km = 42_000),
    )
    private val entries = listOf(
        testEntry("log-1", date = LocalDate(2026, 6, 10), odometerKm = 42_000, paise = 800_000),
    )

    @Test
    fun computesTheRateOverThePeriod() = runTest {
        val snapshot = snapshot()

        assertEquals(LocalDate(2025, 8, 2), snapshot.window.start)
        assertEquals(today, snapshot.window.end)
        assertEquals(12_000, snapshot.cost.kmDriven.km)
        assertEquals(800_000L, snapshot.cost.maintenanceSpend.paise)
        // ₹105/litre over an assumed 15 km/l = ₹7/km, across 12,000 km.
        assertEquals(8_400_000L, snapshot.cost.fuelSpend.paise)
        assertEquals(767L, snapshot.cost.perKm?.paise)
        assertNull(snapshot.cost.shortfall)
    }

    @Test
    fun theFuelPriceIsLookedUpForTheOwnersCityAndTheCarsFuel() = runTest {
        val prices = FakeFuelPriceProvider(testFuelPrice(fuelType = FuelType.CNG))

        val snapshot = snapshot(fuelType = FuelType.CNG, prices = prices)

        assertEquals(listOf<Pair<String?, FuelType>>("Pune" to FuelType.CNG), prices.lookups)
        assertEquals(FuelType.CNG, snapshot.fuelPrice?.fuelType)
    }

    @Test
    fun withNoCitySet_theLookupStillRunsAndCanComeBackEmpty() = runTest {
        val prices = FakeFuelPriceProvider(price = null)

        val snapshot = snapshot(city = null, prices = prices)

        assertEquals(listOf<Pair<String?, FuelType>>(null to FuelType.PETROL), prices.lookups)
        assertTrue(snapshot.fuelEstimateMissing)
        assertEquals(0L, snapshot.cost.fuelSpend.paise)
        // Maintenance alone still has a rate; the screen says the fuel half is missing.
        assertEquals(listOf(SpendCategory.SERVICE), snapshot.cost.categories.map { it.category })
        assertEquals(67L, snapshot.cost.perKm?.paise)
    }

    @Test
    fun withNoCar_nothingIsPricedAndThereIsNoRate() = runTest {
        val prices = FakeFuelPriceProvider(testFuelPrice())

        val snapshot = snapshot(car = null, prices = prices, readings = emptyList())

        assertTrue(prices.lookups.isEmpty(), "a car with no fuel type cannot be priced")
        assertEquals(CostShortfall.NoOdometerReadings, snapshot.cost.shortfall)
        assertNull(snapshot.cost.perKm)
    }

    @Test
    fun aYearIsChartedAsSixTwoMonthBars() = runTest {
        val snapshot = snapshot(period = CostPeriod.Y1)

        assertEquals(6, snapshot.buckets.size)
        // Oldest first, contiguous, and ending on today.
        assertEquals(snapshot.window.start, snapshot.buckets.first().window.start)
        assertEquals(today, snapshot.buckets.last().window.end)
        snapshot.buckets.zipWithNext().forEach { (earlier, later) ->
            assertEquals(earlier.window.end, later.window.start.minusOneDay())
        }
    }

    @Test
    fun threeMonthsIsChartedAsThreeMonthlyBars() = runTest {
        val snapshot = snapshot(period = CostPeriod.M3)

        assertEquals(3, snapshot.buckets.size)
        assertEquals(LocalDate(2026, 5, 2), snapshot.buckets.first().window.start)
    }

    /** The bars are the period cut up, so they have to add back up to it. */
    @Test
    fun theBarsAddUpToThePeriod() = runTest {
        val snapshot = snapshot(period = CostPeriod.Y1)

        assertEquals(snapshot.cost.totalSpend.paise, snapshot.buckets.sumOf { it.spend.paise })
    }

    @Test
    fun trendComparesWithTheWindowBefore() = runTest {
        val snapshot = snapshot(
            period = CostPeriod.M3,
            readings = listOf(
                // ₹1.00/km over the earlier quarter, then nothing but fuel-free driving.
                reading(LocalDate(2026, 1, 30), km = 30_000),
                reading(LocalDate(2026, 4, 15), km = 34_000),
                reading(LocalDate(2026, 6, 15), km = 40_000),
            ),
            entries = listOf(
                testEntry("old", date = LocalDate(2026, 4, 15), odometerKm = 34_000, paise = 400_000),
                testEntry("new", date = LocalDate(2026, 6, 15), odometerKm = 40_000, paise = 300_000),
            ),
            prices = FakeFuelPriceProvider(price = null),
        )

        assertEquals(100L, snapshot.previous.perKm?.paise)
        assertEquals(50L, snapshot.cost.perKm?.paise)
        val trend = assertNotNull(snapshot.trend)
        assertEquals(-50, trend.percentChange)
        assertEquals(false, trend.isUp)
    }

    @Test
    fun everyFigureIsResolvedAgainstTheSameDay() = runTest {
        val snapshot = snapshot()

        assertEquals(today, snapshot.today)
        assertEquals(today, snapshot.window.end)
        assertEquals(snapshot.window.start.minusOneDay(), snapshot.previous.window.end)
    }

    private suspend fun snapshot(
        period: CostPeriod = CostPeriod.Y1,
        fuelType: FuelType = FuelType.PETROL,
        car: Car? = testCar(fuelType),
        city: String? = "Pune",
        prices: FakeFuelPriceProvider = FakeFuelPriceProvider(testFuelPrice()),
        readings: List<OdometerReading> = this.readings,
        entries: List<ServiceLogEntry> = this.entries,
    ): RunningCostSnapshot = ObserveRunningCostUseCase(
        cars = FakeCarRepository(car),
        logs = FakeServiceLogRepository(entries = entries, readings = readings),
        city = cityProvider(city),
        fuelPrices = prices,
        clock = FixedClock(Instant.parse("2026-08-01T09:00:00Z")),
        timeZone = TimeZone.UTC,
    ).invoke(TEST_CAR, period).first()

    private fun LocalDate.minusOneDay(): LocalDate = minus(1, DateTimeUnit.DAY)
}
