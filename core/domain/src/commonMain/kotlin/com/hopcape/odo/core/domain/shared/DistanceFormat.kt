package com.hopcape.odo.core.domain.shared

/**
 * Distance rendering for [Distance] — the one place a distance becomes a display string,
 * shared across features (odometer readings, distance driven). Indian digit grouping
 * matches [Amount.formatRupees].
 */

/** "22,200 km" — Indian digit grouping. Kept until every screen reads a chosen unit. */
fun Distance.formatKm(): String = format(DistanceUnit.KILOMETRE)

/** "22,200 km" / "13,795 mi" — the reading in [unit], Indian digit grouping. */
fun Distance.format(unit: DistanceUnit): String =
    "${groupIndianDigits(displayValue(unit).toLong())} ${unit.suffix()}"

/** The short unit label a number is followed by: "km" or "mi". */
fun DistanceUnit.suffix(): String = when (this) {
    DistanceUnit.KILOMETRE -> "km"
    DistanceUnit.MILE -> "mi"
}
