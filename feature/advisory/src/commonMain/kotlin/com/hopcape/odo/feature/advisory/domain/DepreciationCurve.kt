package com.hopcape.odo.feature.advisory.domain

import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.shared.VehicleSegment

/**
 * The hand-entered numbers behind a resale estimate.
 *
 * Tier 0 reference data: entered once, true for every owner from second zero, and the only
 * reason this screen can answer on day 1 rather than after a year of history. It belongs
 * with the labour rates and job prices in the reference tables eventually; it is Kotlin
 * today because no row of it has been typed yet and a screen that renders nothing is worse
 * than one that renders a stated estimate.
 *
 * Everything here is a **band**, not a price. A model-level price table would never be
 * finished and would still leave the tail empty, so the axis is the segment.
 */
internal object DepreciationCurve {

    /**
     * Mid-variant ex-showroom price, in paise, by segment and fuel.
     *
     * The anchor every other factor scales. Diesel and CNG carry their usual premium over
     * petrol; electric is priced separately because its curve is nothing like the others'.
     */
    fun newPricePaise(segment: VehicleSegment, fuel: FuelType): Long {
        val base = when (segment) {
            VehicleSegment.HATCHBACK -> 8_00_000_00L
            VehicleSegment.SEDAN -> 12_00_000_00L
            VehicleSegment.SUV -> 15_00_000_00L
            VehicleSegment.MUV -> 14_00_000_00L
        }
        val premium = when (fuel) {
            FuelType.DIESEL -> 1.15
            FuelType.CNG -> 1.08
            FuelType.ELECTRIC -> 1.60
            else -> 1.0
        }
        return (base * premium).toLong()
    }

    /**
     * What fraction of the ex-showroom price a car of [age] years still fetches.
     *
     * Indian resale, not accounting depreciation: the first year takes the biggest bite and
     * the curve flattens after year five. Past the table it loses three points of the
     * remaining value a year and stops at [FLOOR], because a running car is never worthless.
     */
    fun retentionAt(age: Int): Double {
        if (age <= 0) return RETENTION.first()
        RETENTION.getOrNull(age)?.let { return it }
        val beyond = age - RETENTION.lastIndex
        var value = RETENTION.last()
        repeat(beyond) { value *= 0.97 }
        return maxOf(value, FLOOR)
    }

    /**
     * How the reading moves the price against what the car's age would predict.
     *
     * A low-kilometre car of its year is worth more and a high-kilometre one less, but only
     * so much: past the clamp the buyer stops paying for the odometer and starts pricing the
     * condition, which no table can see.
     */
    fun odometerFactor(odometerKm: Int, age: Int): Double {
        val expected = EXPECTED_KM_PER_YEAR * maxOf(age, 1)
        val delta = (expected - odometerKm).toDouble() / KM_PER_STEP
        return (1.0 + delta * STEP).coerceIn(1.0 - MAX_KM_SWING, 1.0 + MAX_KM_SWING)
    }

    /**
     * The city's effect on price, by the tier the city catalog already carries.
     *
     * A metro has more buyers and thinner discounts; a smaller city has neither. The tier is
     * on `City` already, so this costs no new data.
     */
    fun cityFactor(tier: Int?): Double = when (tier) {
        1 -> 1.0
        2 -> 0.97
        3 -> 0.94
        else -> 0.97
    }

    /**
     * What a complete, documented service record adds, as a low–high fraction.
     *
     * Deliberately a range and deliberately modest. A buyer pays for proof, but nobody pays
     * a fifth more for a folder of bills, and quoting a single number here would be the
     * false precision the PRD forbids.
     */
    const val RECORD_PREMIUM_LOW: Double = 0.04
    const val RECORD_PREMIUM_HIGH: Double = 0.09

    /** Roughly one service a year is what a record is measured against. */
    const val SERVICES_PER_YEAR: Int = 1

    private const val EXPECTED_KM_PER_YEAR = 12_000
    private const val KM_PER_STEP = 10_000.0
    private const val STEP = 0.02
    private const val MAX_KM_SWING = 0.12
    private const val FLOOR = 0.12

    /** Retention by age in years; index 0 is a car still in its first year. */
    private val RETENTION = listOf(
        1.0, 0.85, 0.78, 0.72, 0.66, 0.60, 0.54, 0.48, 0.43, 0.38, 0.34,
    )
}
