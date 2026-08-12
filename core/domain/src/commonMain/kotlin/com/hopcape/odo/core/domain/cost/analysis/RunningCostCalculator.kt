package com.hopcape.odo.core.domain.cost.analysis

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.cost.model.CostBreakdown
import com.hopcape.odo.core.domain.cost.model.CostShortfall
import com.hopcape.odo.core.domain.cost.model.CostWindow
import com.hopcape.odo.core.domain.cost.model.RunningCost
import com.hopcape.odo.core.domain.cost.model.SpendCategory
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance

/**
 * Works out what a car cost per kilometre over a window.
 *
 * A pure domain service — no repository, no clock, no fuel-price lookup. It takes the
 * logs, the odometer readings and an already-resolved fuel rate, and returns the numbers.
 * It lives in `:core:domain` rather than in `:feature:cost-tracker` because three surfaces
 * need the same figure: the running-cost screen, the home tile, and (once it is built) the
 * health score's cost points. A feature may not import another feature, so the math has to
 * sit in the shared kernel — the same reason
 * [OdometerTimeline][com.hopcape.odo.core.domain.servicelog.analysis.OdometerTimeline] does.
 *
 * **Distance** comes from the odometer, which is mandatory on every log, so no separate
 * trip logging is needed: the car's reading at the end of the window minus its reading at
 * the start.
 *
 * **Fuel** is never logged (the PRD drops manual fuel entry as friction). The caller
 * resolves a paise-per-km rate from the city's fuel price and the car's assumed efficiency
 * and passes it in; `null` means no rate could be resolved, and then fuel simply does not
 * appear. Whatever shows this must label it as an estimate.
 */
object RunningCostCalculator {

    /**
     * How far a car must have moved before a ₹/km rate is worth quoting.
     *
     * A single ₹6,000 service over 40 km reads as ₹150/km, which is true arithmetic and a
     * useless number. A hundred kilometres is roughly a week of city driving — enough for
     * the rate to stop swinging on one bill.
     */
    const val MIN_DISTANCE_KM: Int = 100

    /**
     * The running cost over [window].
     *
     * @param entries the car's service logs; only those dated inside the window count.
     * @param readings every known odometer reading for the car (its logs plus the
     *   onboarding baseline) — readings from before the window are used too, as the
     *   anchor the distance is measured from.
     * @param fuelRatePerKm estimated fuel cost per kilometre, or `null` when the city or
     *   the price is unknown.
     */
    fun compute(
        window: CostWindow,
        entries: List<ServiceLogEntry>,
        readings: List<OdometerReading>,
        fuelRatePerKm: Amount? = null,
    ): RunningCost {
        val kmDriven = distanceOver(window, readings)
        val logged = entries.filter { it.serviceDate in window }
        val spendByCategory = allocate(logged)
        val maintenanceSpend = amount(spendByCategory.values.sum())
        val fuelSpend = fuelRatePerKm?.times(kmDriven?.km ?: 0) ?: Amount.ZERO

        val shortfall = shortfallFor(kmDriven)
        val distance = kmDriven ?: distance(0)
        val totalSpend = maintenanceSpend + fuelSpend

        return RunningCost(
            window = window,
            kmDriven = distance,
            maintenanceSpend = maintenanceSpend,
            fuelSpend = fuelSpend,
            perKm = if (shortfall == null) totalSpend.perKm(distance) else null,
            categories = breakdown(
                spendByCategory = spendByCategory,
                fuelSpend = fuelSpend,
                distance = distance,
                hasRate = shortfall == null,
            ),
            shortfall = shortfall,
        )
    }

    /**
     * How far the car moved during [window]: its reading at the end minus its reading at
     * the start. `null` when nothing was ever read on or before the window's end.
     *
     * The start anchor is the last reading taken *before or on* the window's first day,
     * even though that reading is older than the window — that is the distance the owner
     * actually covered inside it. Only when no such reading exists does the window's own
     * earliest reading anchor it, which is the PRD's "earliest to latest entry" rule and
     * slightly overstates the rate for a car whose history starts mid-window.
     *
     * Readings are compared by kilometres rather than by date order, so an out-of-order
     * row cannot produce a negative distance.
     */
    private fun distanceOver(window: CostWindow, readings: List<OdometerReading>): Distance? {
        val end = readings.filter { it.date <= window.end }.maxOfOrNull { it.odometer.km } ?: return null
        val start = readings.filter { it.date <= window.start }.maxOfOrNull { it.odometer.km }
            ?: readings.filter { it.date in window }.minOfOrNull { it.odometer.km }
            ?: end
        return distance((end - start).coerceAtLeast(0))
    }

    private fun shortfallFor(kmDriven: Distance?): CostShortfall? = when {
        kmDriven == null -> CostShortfall.NoOdometerReadings
        kmDriven.km < MIN_DISTANCE_KM -> CostShortfall.NotEnoughDistance(
            kmDriven = kmDriven.km,
            requiredKm = MIN_DISTANCE_KM,
        )

        else -> null
    }

    /**
     * Split the logged spend into buckets. The entry's own total is what gets split — line
     * items are a breakdown and are not required to add up to it — so the buckets always
     * sum back to what the owner paid.
     *
     * An itemised entry is split in proportion to its lines, so a ₹8,000 visit that was
     * mostly brake work does not land wholly in one bucket. An entry without lines goes to
     * the bucket its tags name ([SpendCategory.forEntry]).
     */
    private fun allocate(entries: List<ServiceLogEntry>): Map<SpendCategory, Long> {
        val totals = mutableMapOf<SpendCategory, Long>()
        entries.forEach { entry ->
            allocateEntry(entry).forEach { (category, paise) ->
                totals[category] = (totals[category] ?: 0L) + paise
            }
        }
        return totals
    }

    private fun allocateEntry(entry: ServiceLogEntry): Map<SpendCategory, Long> {
        val total = entry.totalAmount.paise
        if (total == 0L) return emptyMap()

        val lineTotals = entry.lineItems
            .filter { it.amount.paise > 0 }
            .groupBy { SpendCategory.of(it.category) }
            .mapValues { (_, lines) -> lines.sumOf { it.amount.paise } }
        val lineSum = lineTotals.values.sum()
        if (lineSum == 0L) return mapOf(SpendCategory.forEntry(entry.categories) to total)

        val shares = lineTotals.mapValues { (_, paise) -> total * paise / lineSum }
        // Integer division loses up to a paise per bucket; the largest bucket absorbs it so
        // the split still adds up to the entry's total.
        val remainder = total - shares.values.sum()
        val largest = shares.entries.maxWith(compareBy({ it.value }, { -it.key.ordinal })).key
        return shares.mapValues { (category, paise) -> if (category == largest) paise + remainder else paise }
    }

    /**
     * The "where it goes" rows, in a fixed order and only for buckets that cost something —
     * a zero row tells the owner nothing.
     */
    private fun breakdown(
        spendByCategory: Map<SpendCategory, Long>,
        fuelSpend: Amount,
        distance: Distance,
        hasRate: Boolean,
    ): List<CostBreakdown> = SpendCategory.entries.mapNotNull { category ->
        val spend = when (category) {
            SpendCategory.FUEL -> fuelSpend
            else -> amount(spendByCategory[category] ?: 0L)
        }
        if (spend.paise == 0L) return@mapNotNull null
        CostBreakdown(
            category = category,
            spend = spend,
            perKm = if (hasRate) spend.perKm(distance) else null,
        )
    }

    /** Both helpers take values this file has already kept non-negative. */
    private fun amount(paise: Long): Amount = Amount.of(paise).getOrElse { Amount.ZERO }

    private fun distance(km: Int): Distance = Distance.of(km).getOrElse { error("negative distance $km") }
}
