package com.hopcape.odo.core.domain.cost.fuel

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.shared.Amount

/**
 * How far a car is assumed to go on one unit of fuel, and what that makes it cost per
 * kilometre.
 *
 * Odo does not ask owners to log fuel — the PRD drops that as friction — so the fuel half
 * of the running cost is an estimate, and this is the assumption behind it. Real-world
 * city figures for the Indian hatchback/sedan mix Odo is built for, deliberately on the
 * conservative side of the manufacturer claims, which are measured in test conditions
 * nobody drives in.
 *
 * A pure policy, like
 * [DocumentReminderPolicy][com.hopcape.odo.core.domain.document.policy.DocumentReminderPolicy]:
 * no lookup, no clock. When a car eventually carries its own measured efficiency, this
 * becomes the fallback rather than the answer, and only this file changes.
 *
 * Anything that shows a figure built on these numbers must call it an estimate.
 */
object FuelEfficiencyPolicy {

    /**
     * Assumed kilometres per unit of fuel — per litre for petrol and diesel, per kilogram
     * for CNG, per kWh for electric.
     */
    fun kmPerUnit(fuelType: FuelType): Int = when (fuelType) {
        FuelType.PETROL -> PETROL_KM_PER_LITRE
        FuelType.DIESEL -> DIESEL_KM_PER_LITRE
        FuelType.CNG -> CNG_KM_PER_KG
        FuelType.ELECTRIC -> ELECTRIC_KM_PER_KWH
    }

    /**
     * What [price] works out to per kilometre, rounded to the nearest paise — the fuel
     * rate [RunningCostCalculator][com.hopcape.odo.core.domain.cost.analysis.RunningCostCalculator]
     * multiplies by the distance driven.
     */
    fun ratePerKm(price: FuelPrice): Amount {
        val km = kmPerUnit(price.fuelType)
        return Amount.of((price.pricePerUnit.paise + km / 2) / km).getOrElse { Amount.ZERO }
    }

    /** City driving on a petrol hatchback, well under the claimed 20+ km/l. */
    private const val PETROL_KM_PER_LITRE = 15

    /** Diesel goes further on a litre; the same conservative city figure. */
    private const val DIESEL_KM_PER_LITRE = 18

    /** CNG is sold and consumed by the kilogram, so this is km per kg, not per litre. */
    private const val CNG_KM_PER_KG = 22

    /** Electric, per kWh drawn — real-world city running, not the rated range. */
    private const val ELECTRIC_KM_PER_KWH = 7
}
