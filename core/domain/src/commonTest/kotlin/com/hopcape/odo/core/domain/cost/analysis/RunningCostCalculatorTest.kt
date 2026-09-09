package com.hopcape.odo.core.domain.cost.analysis

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.model.CostShortfall
import com.hopcape.odo.core.domain.cost.model.CostWindow
import com.hopcape.odo.core.domain.cost.model.SpendCategory
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItem
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RunningCostCalculatorTest {

    private val window = CostWindow(start = LocalDate(2026, 1, 1), end = LocalDate(2026, 6, 30))

    @Test
    fun distanceIsMeasuredFromTheReadingBeforeTheWindow() {
        val cost = RunningCostCalculator.compute(
            window = window,
            entries = listOf(entry("1", date = LocalDate(2026, 3, 10), km = 44_000, paise = 500_000)),
            readings = listOf(
                reading(LocalDate(2025, 12, 20), km = 40_000),
                reading(LocalDate(2026, 3, 10), km = 44_000),
            ),
        )

        // 44,000 − 40,000: the December reading anchors it, even though it predates the window.
        assertEquals(4_000, cost.kmDriven.km)
        assertEquals(500_000L, cost.maintenanceSpend.paise)
        assertEquals(125L, cost.perKm?.paise)
        assertNull(cost.shortfall)
    }

    /**
     * A DECLARED entry is the owner remembering that a service happened, with no bill behind
     * it. Its zero would not widen the rate, it would pull it down — the car would read
     * cheaper to run than it is.
     */
    @Test
    fun aDeclaredServiceIsLeftOutOfTheSpend() {
        val cost = RunningCostCalculator.compute(
            window = window,
            entries = listOf(
                entry("1", date = LocalDate(2026, 3, 10), km = 44_000, paise = 500_000),
                entry(
                    "2",
                    date = LocalDate(2026, 4, 1),
                    km = 45_000,
                    paise = 0,
                    source = LogSource.DECLARED,
                ),
            ),
            readings = listOf(
                reading(LocalDate(2025, 12, 20), km = 40_000),
                reading(LocalDate(2026, 3, 10), km = 44_000),
            ),
        )

        // The paid service alone, over the same 4,000 km — not halved by a costless row.
        assertEquals(500_000L, cost.maintenanceSpend.paise)
        assertEquals(125L, cost.perKm?.paise)
    }

    /** Its odometer is still a fact, and distance is read from the readings, not the logs. */
    @Test
    fun aDeclaredServicesReadingStillCountsTowardsDistance() {
        val cost = RunningCostCalculator.compute(
            window = window,
            entries = listOf(
                entry(
                    "1",
                    date = LocalDate(2026, 4, 1),
                    km = 45_000,
                    paise = 0,
                    source = LogSource.DECLARED,
                ),
            ),
            readings = listOf(
                reading(LocalDate(2025, 12, 20), km = 40_000),
                reading(LocalDate(2026, 4, 1), km = 45_000),
            ),
        )

        assertEquals(5_000, cost.kmDriven.km)
    }

    @Test
    fun withNoEarlierReading_theWindowsFirstReadingAnchorsIt() {
        val cost = RunningCostCalculator.compute(
            window = window,
            entries = emptyList(),
            readings = listOf(
                reading(LocalDate(2026, 2, 1), km = 10_000),
                reading(LocalDate(2026, 5, 1), km = 13_000),
            ),
        )

        assertEquals(3_000, cost.kmDriven.km)
    }

    @Test
    fun readingsAfterTheWindow_doNotCount() {
        val cost = RunningCostCalculator.compute(
            window = window,
            entries = emptyList(),
            readings = listOf(
                reading(LocalDate(2026, 2, 1), km = 10_000),
                reading(LocalDate(2026, 5, 1), km = 13_000),
                reading(LocalDate(2026, 8, 1), km = 20_000),
            ),
        )

        assertEquals(3_000, cost.kmDriven.km)
    }

    @Test
    fun withNoReadingsAtAll_thereIsNoRate() {
        val cost = RunningCostCalculator.compute(
            window = window,
            entries = listOf(entry("1", date = LocalDate(2026, 3, 10), km = 44_000, paise = 500_000)),
            readings = emptyList(),
        )

        assertEquals(CostShortfall.NoOdometerReadings, cost.shortfall)
        assertNull(cost.perKm)
        assertEquals(0, cost.kmDriven.km)
        // The spend is still reported — only the rate is withheld.
        assertEquals(500_000L, cost.maintenanceSpend.paise)
        assertNull(cost.categories.single().perKm)
    }

    @Test
    fun tooFewKilometres_yieldNoRate() {
        val cost = RunningCostCalculator.compute(
            window = window,
            entries = listOf(entry("1", date = LocalDate(2026, 3, 10), km = 40_060, paise = 600_000)),
            readings = listOf(
                reading(LocalDate(2026, 1, 5), km = 40_000),
                reading(LocalDate(2026, 3, 10), km = 40_060),
            ),
        )

        assertEquals(CostShortfall.NotEnoughDistance(kmDriven = 60, requiredKm = 100), cost.shortfall)
        assertNull(cost.perKm)
    }

    @Test
    fun onlyLogsInsideTheWindowCount() {
        val cost = RunningCostCalculator.compute(
            window = window,
            entries = listOf(
                entry("before", date = LocalDate(2025, 12, 31), km = 40_000, paise = 900_000),
                entry("inside", date = LocalDate(2026, 3, 10), km = 44_000, paise = 500_000),
                entry("after", date = LocalDate(2026, 7, 1), km = 46_000, paise = 700_000),
            ),
            readings = listOf(
                reading(LocalDate(2025, 12, 31), km = 40_000),
                reading(LocalDate(2026, 3, 10), km = 44_000),
                reading(LocalDate(2026, 7, 1), km = 46_000),
            ),
        )

        assertEquals(500_000L, cost.maintenanceSpend.paise)
    }

    @Test
    fun fuelIsEstimatedFromTheDistanceDriven() {
        val cost = RunningCostCalculator.compute(
            window = window,
            entries = listOf(entry("1", date = LocalDate(2026, 3, 10), km = 44_000, paise = 500_000)),
            readings = listOf(
                reading(LocalDate(2025, 12, 20), km = 40_000),
                reading(LocalDate(2026, 3, 10), km = 44_000),
            ),
            fuelRatePerKm = amount(700),
        )

        // 4,000 km × ₹7/km.
        assertEquals(2_800_000L, cost.fuelSpend.paise)
        assertEquals(3_300_000L, cost.totalSpend.paise)
        assertEquals(825L, cost.perKm?.paise)
        assertEquals(700L, cost.categories.first { it.category == SpendCategory.FUEL }.perKm?.paise)
    }

    @Test
    fun withNoFuelRate_thereIsNoFuelRow() {
        val cost = RunningCostCalculator.compute(
            window = window,
            entries = listOf(entry("1", date = LocalDate(2026, 3, 10), km = 44_000, paise = 500_000)),
            readings = listOf(
                reading(LocalDate(2025, 12, 20), km = 40_000),
                reading(LocalDate(2026, 3, 10), km = 44_000),
            ),
        )

        assertEquals(0L, cost.fuelSpend.paise)
        assertEquals(listOf(SpendCategory.SERVICE), cost.categories.map { it.category })
    }

    @Test
    fun anUntaggedEntry_landsInService() {
        val cost = RunningCostCalculator.compute(
            window = window,
            entries = listOf(entry("1", date = LocalDate(2026, 3, 10), km = 44_000, paise = 500_000)),
            readings = listOf(
                reading(LocalDate(2025, 12, 20), km = 40_000),
                reading(LocalDate(2026, 3, 10), km = 44_000),
            ),
        )

        val row = cost.categories.single()
        assertEquals(SpendCategory.SERVICE, row.category)
        assertEquals(500_000L, row.spend.paise)
    }

    @Test
    fun aTaggedEntryWithoutLines_goesWholeToItsBucket() {
        val cost = RunningCostCalculator.compute(
            window = window,
            entries = listOf(
                entry(
                    "1",
                    date = LocalDate(2026, 3, 10),
                    km = 44_000,
                    paise = 500_000,
                    categories = setOf(ServiceCategory.OIL_CHANGE, ServiceCategory.BRAKES),
                ),
            ),
            readings = listOf(
                reading(LocalDate(2025, 12, 20), km = 40_000),
                reading(LocalDate(2026, 3, 10), km = 44_000),
            ),
        )

        val row = cost.categories.single()
        assertEquals(SpendCategory.REPAIRS, row.category)
        assertEquals(500_000L, row.spend.paise)
    }

    @Test
    fun anItemisedEntry_isSplitInProportionAndStillAddsUp() {
        val cost = RunningCostCalculator.compute(
            window = window,
            entries = listOf(
                entry(
                    "1",
                    date = LocalDate(2026, 3, 10),
                    km = 44_000,
                    // ₹8,000 total against ₹6,000 of brake work and ₹2,000 of oil change.
                    paise = 800_000,
                    lineItems = listOf(
                        line(ServiceCategory.BRAKES, 600_000),
                        line(ServiceCategory.OIL_CHANGE, 200_000),
                    ),
                ),
            ),
            readings = listOf(
                reading(LocalDate(2025, 12, 20), km = 40_000),
                reading(LocalDate(2026, 3, 10), km = 44_000),
            ),
        )

        val byCategory = cost.categories.associate { it.category to it.spend.paise }
        assertEquals(600_000L, byCategory[SpendCategory.REPAIRS])
        assertEquals(200_000L, byCategory[SpendCategory.SERVICE])
        assertEquals(800_000L, cost.maintenanceSpend.paise)
    }

    @Test
    fun anItemisedSplitThatDoesNotDivide_keepsTheEntrysTotal() {
        val cost = RunningCostCalculator.compute(
            window = window,
            entries = listOf(
                entry(
                    "1",
                    date = LocalDate(2026, 3, 10),
                    km = 44_000,
                    paise = 100_001,
                    lineItems = listOf(
                        line(ServiceCategory.BRAKES, 2),
                        line(ServiceCategory.OIL_CHANGE, 1),
                    ),
                ),
            ),
            readings = listOf(
                reading(LocalDate(2025, 12, 20), km = 40_000),
                reading(LocalDate(2026, 3, 10), km = 44_000),
            ),
        )

        assertEquals(100_001L, cost.categories.sumOf { it.spend.paise })
        assertEquals(100_001L, cost.maintenanceSpend.paise)
        // The rounding remainder goes to the larger bucket, not to whichever came first.
        assertEquals(66_668L, cost.categories.first { it.category == SpendCategory.REPAIRS }.spend.paise)
        assertEquals(33_333L, cost.categories.first { it.category == SpendCategory.SERVICE }.spend.paise)
    }

    @Test
    fun trendComparesTheRateWithTheWindowBefore() {
        val previous = RunningCostCalculator.compute(
            window = window.previous(),
            entries = listOf(entry("0", date = LocalDate(2025, 9, 1), km = 36_000, paise = 400_000)),
            readings = listOf(
                reading(LocalDate(2025, 6, 1), km = 32_000),
                reading(LocalDate(2025, 9, 1), km = 36_000),
            ),
        )
        val current = RunningCostCalculator.compute(
            window = window,
            entries = listOf(entry("1", date = LocalDate(2026, 3, 10), km = 44_000, paise = 500_000)),
            readings = listOf(
                reading(LocalDate(2025, 12, 20), km = 40_000),
                reading(LocalDate(2026, 3, 10), km = 44_000),
            ),
        )

        // ₹1.00/km → ₹1.25/km.
        assertEquals(100L, previous.perKm?.paise)
        assertEquals(125L, current.perKm?.paise)
        val trend = current.trendAgainst(previous)
        assertEquals(25, trend?.percentChange)
        assertEquals(25, trend?.magnitude)
        assertEquals(true, trend?.isUp)
    }

    @Test
    fun aCheaperWindow_trendsDown() {
        val previous = RunningCostCalculator.compute(
            window = window.previous(),
            entries = listOf(entry("0", date = LocalDate(2025, 9, 1), km = 36_000, paise = 800_000)),
            readings = listOf(
                reading(LocalDate(2025, 6, 1), km = 32_000),
                reading(LocalDate(2025, 9, 1), km = 36_000),
            ),
        )
        val current = RunningCostCalculator.compute(
            window = window,
            entries = listOf(entry("1", date = LocalDate(2026, 3, 10), km = 44_000, paise = 600_000)),
            readings = listOf(
                reading(LocalDate(2025, 12, 20), km = 40_000),
                reading(LocalDate(2026, 3, 10), km = 44_000),
            ),
        )

        val trend = current.trendAgainst(previous)
        assertEquals(-25, trend?.percentChange)
        assertEquals(25, trend?.magnitude)
        assertEquals(false, trend?.isUp)
    }

    @Test
    fun withNothingToCompareAgainst_thereIsNoTrend() {
        val empty = RunningCostCalculator.compute(window = window.previous(), entries = emptyList(), readings = emptyList())
        val current = RunningCostCalculator.compute(
            window = window,
            entries = listOf(entry("1", date = LocalDate(2026, 3, 10), km = 44_000, paise = 500_000)),
            readings = listOf(
                reading(LocalDate(2025, 12, 20), km = 40_000),
                reading(LocalDate(2026, 3, 10), km = 44_000),
            ),
        )

        assertNull(current.trendAgainst(empty))
        assertNull(empty.trendAgainst(current))
    }

    @Test
    fun aFreeWindowBefore_yieldsNoTrend() {
        val free = RunningCostCalculator.compute(
            window = window.previous(),
            entries = emptyList(),
            readings = listOf(
                reading(LocalDate(2025, 6, 1), km = 32_000),
                reading(LocalDate(2025, 9, 1), km = 36_000),
            ),
        )
        val current = RunningCostCalculator.compute(
            window = window,
            entries = listOf(entry("1", date = LocalDate(2026, 3, 10), km = 44_000, paise = 500_000)),
            readings = listOf(
                reading(LocalDate(2025, 12, 20), km = 40_000),
                reading(LocalDate(2026, 3, 10), km = 44_000),
            ),
        )

        assertEquals(0L, free.perKm?.paise)
        assertNull(current.trendAgainst(free))
    }

    private fun entry(
        id: String,
        date: LocalDate,
        km: Int,
        paise: Long,
        categories: Set<ServiceCategory> = emptySet(),
        lineItems: List<ServiceLogLineItem> = emptyList(),
        source: LogSource = LogSource.MANUAL,
    ) = ServiceLogEntry.reconstitute(
        id = ServiceLogId(id),
        carId = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        serviceDate = date,
        odometerKm = km,
        totalAmountPaise = paise,
        workshopName = null,
        notes = null,
        source = source,
        billId = null,
        categories = categories,
        lineItems = lineItems,
    )

    private fun reading(date: LocalDate, km: Int) =
        OdometerReading(logId = null, date = date, odometer = Distance.of(km).getOrNull()!!)

    private fun line(category: ServiceCategory, paise: Long) =
        ServiceLogLineItem(label = null, category = category, amount = amount(paise))

    private fun amount(paise: Long): Amount = Amount.of(paise).getOrNull()!!
}
