package com.hopcape.odo.core.domain.cost.analysis

import com.hopcape.odo.core.domain.cost.model.FuelFill

/**
 * How far the car went on a tank, measured rather than assumed.
 *
 * Until fills existed, every mileage figure in the app came from
 * [FuelEfficiencyPolicy][com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyPolicy] — a
 * table of typical numbers by fuel type. Two fills with odometer readings replace that guess
 * with the owner's own car: the distance between them, divided by the fuel that went in.
 *
 * The fuel counted is the **earlier** fill's, not the later one's. What the earlier tank
 * bought is the distance driven before the next visit; the fuel just added has not been
 * burned yet.
 *
 * Every result here is refused rather than approximated when the inputs cannot support it.
 * A wrong mileage is worse than none: it is the number an owner would judge a service or a
 * fuel brand by.
 */
object TankMileage {

    /**
     * The shortest gap between two fills that is worth quoting a figure for.
     *
     * Two fills fifteen kilometres apart divide a whole tank over a short trip and produce
     * a number that swings wildly with how full each was. Fifty kilometres is roughly a
     * week of city driving.
     */
    const val MIN_DISTANCE_KM: Int = 50

    /**
     * Distance per unit of fuel between [previous] and [latest], or `null` when the pair
     * cannot support a figure.
     *
     * Refused when the fills are in the wrong order, when either fill has no odometer
     * reading, when the odometer went backwards or did not move enough, or when the earlier
     * fill has no quantity recorded. Each of those would produce a number, and none of them
     * would produce a true one.
     *
     * A missing reading is now an ordinary case rather than an impossible one: the confirm
     * step no longer demands the odometer, because a detected fill reaches the owner while
     * they are still at the pump. Those fills count towards what fuel cost and simply take
     * no part in what it bought.
     */
    fun between(previous: FuelFill, latest: FuelFill): Double? {
        if (latest.filledOn < previous.filledOn) return null
        if (previous.quantityMilli <= 0) return null

        val previousKm = previous.odometer?.km ?: return null
        val latestKm = latest.odometer?.km ?: return null

        val km = latestKm - previousKm
        if (km < MIN_DISTANCE_KM) return null

        val units = previous.quantityMilli.toDouble() / FuelFillMilli
        return km / units
    }

    /**
     * The mileage of the tank ending at [latest], given the car's fills newest-first.
     *
     * The convenience the confirm step uses: it has just written a fill and wants the one
     * line the owner cares about. `null` when there is no earlier fill to measure from,
     * which is always the case for the first fill ever logged.
     */
    fun forLatest(fillsNewestFirst: List<FuelFill>): Double? {
        val latest = fillsNewestFirst.firstOrNull() ?: return null
        val previous = fillsNewestFirst.getOrNull(1) ?: return null
        return between(previous = previous, latest = latest)
    }

    /**
     * The car's average over every pair of consecutive fills it has, or `null` when no pair
     * qualifies.
     *
     * Used to say whether the tank just logged was better or worse than usual. Averaging
     * the per-tank figures rather than dividing total distance by total fuel keeps one
     * unusually long gap from dominating the answer.
     */
    fun average(fillsNewestFirst: List<FuelFill>): Double? {
        val figures = fillsNewestFirst
            .zipWithNext { latest, previous -> between(previous = previous, latest = latest) }
            .filterNotNull()
        if (figures.isEmpty()) return null
        return figures.average()
    }

    /** Thousandths of a unit, the resolution quantities are stored at. */
    private const val FuelFillMilli: Double = 1_000.0
}
