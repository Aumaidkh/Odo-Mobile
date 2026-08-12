package com.hopcape.odo.core.domain.cost.fuel

/**
 * How an assumed fuel efficiency is written: distance per unit of fuel ("15 km/l") or fuel
 * per hundred kilometres ("6.7 L/100km").
 *
 * Both say the same thing about the same car. Which one reads as normal depends on where
 * the owner learned to drive, so it is a preference rather than a fact, and it changes
 * nothing the app computes — [FuelEfficiencyPolicy] stays in km per unit throughout.
 *
 * The fuel side of that is [FuelUnit], so the pair covers CNG ("22 km/kg") and electric
 * ("7 km/kWh", "14.3 kWh/100km") without a special case.
 */
enum class FuelEfficiencyUnit {
    /** Distance per unit of fuel — "15 km/l". */
    DISTANCE_PER_UNIT,

    /** Fuel per hundred kilometres — "6.7 L/100km". */
    UNITS_PER_100KM,
    ;

    companion object {
        /** What an owner gets before they choose; km/l is how mileage is quoted in India. */
        val Default: FuelEfficiencyUnit = DISTANCE_PER_UNIT
    }
}

/**
 * The assumed efficiency as a display string — "15 km/l", "6.7 L/100km", "22 km/kg".
 *
 * [kmPerUnit] is [FuelEfficiencyPolicy.kmPerUnit], so this is always an assumption and
 * whatever shows it has to say so.
 *
 * Deliberately in kilometres even when distances are shown in miles: the two settings are
 * separate, and there is no mpg option to switch to — the units sheet offers km/l and
 * L/100km only.
 */
fun FuelEfficiencyUnit.format(kmPerUnit: Int, fuelUnit: FuelUnit): String = when (this) {
    FuelEfficiencyUnit.DISTANCE_PER_UNIT -> "$kmPerUnit km/${fuelUnit.abbreviation()}"
    FuelEfficiencyUnit.UNITS_PER_100KM -> {
        // Tenths, integer math: 100 km worth of fuel, rounded to one decimal place.
        val tenths = (1000 + kmPerUnit / 2) / kmPerUnit
        val label = "${tenths / 10}.${tenths % 10}"
        "$label ${fuelUnit.abbreviation(capitalised = true)}/100km"
    }
}

/**
 * How the unit is written next to a number: "l", "kg", "kWh". [capitalised] is for the
 * leading position in "L/100km", where a lower-case l next to a digit reads as a one.
 */
private fun FuelUnit.abbreviation(capitalised: Boolean = false): String = when (this) {
    FuelUnit.LITRE -> if (capitalised) "L" else "l"
    FuelUnit.KILOGRAM -> "kg"
    FuelUnit.KILOWATT_HOUR -> "kWh"
}
