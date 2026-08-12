package com.hopcape.odo.core.domain.shared

import arrow.core.Either
import arrow.core.getOrElse
import kotlin.math.roundToInt

/**
 * The unit a distance is shown in and typed in.
 *
 * Storage is always kilometres: [Distance] holds km, every odometer column is km, and the
 * fairness and cost math is in km. This only changes what a screen prints and what a
 * number the owner types means. Nothing is ever converted on the way into the database, so
 * switching units cannot alter a stored reading or a past figure.
 */
enum class DistanceUnit {
    KILOMETRE,
    MILE,
    ;

    companion object {
        /** What an owner gets before they choose — Odo is an India-first app (PRD). */
        val Default: DistanceUnit = KILOMETRE
    }
}

/** The international mile, exactly 1.609344 km. */
private const val KM_PER_MILE = 1.609344

/**
 * The whole number shown for this distance in [unit] — 54,000 km reads as 33,554 mi.
 *
 * Rounded, not truncated: an odometer shown one unit short of the reading it came from
 * looks like a data-entry mistake to the person who read it off their dashboard.
 */
fun Distance.displayValue(unit: DistanceUnit): Int = when (unit) {
    DistanceUnit.KILOMETRE -> km
    DistanceUnit.MILE -> (km / KM_PER_MILE).roundToInt()
}

/**
 * A per-kilometre rate restated per [unit] — Rs. 12.30/km is Rs. 19.79/mi.
 *
 * Money itself never changes: what a service cost is what it cost. Only a rate quoted
 * *per distance* moves, because the distance it is divided by moved.
 */
fun Amount.perDistanceUnit(unit: DistanceUnit): Amount = when (unit) {
    DistanceUnit.KILOMETRE -> this
    // Integer math, like every other money path: paise never touch a Double.
    DistanceUnit.MILE -> Amount.of((paise * 1_609_344 + 500_000) / 1_000_000).getOrElse { Amount.ZERO }
}

/**
 * The kilometres to store for [displayed] typed in [unit].
 *
 * [current] is the reading already on file. When [displayed] renders the same as [current]
 * in [unit], [current] is returned untouched — re-typing the number already on screen must
 * not count as a change. Without that, a miles round trip can land a kilometre below the
 * stored reading, and the odometer rules would reject the owner's own unchanged number as
 * going backwards.
 */
fun Distance.Companion.of(
    displayed: Int?,
    unit: DistanceUnit,
    current: Distance? = null,
): Either<DomainError, Distance> {
    if (displayed != null && current != null && current.displayValue(unit) == displayed) {
        return Either.Right(current)
    }
    val km = when {
        displayed == null -> null
        unit == DistanceUnit.KILOMETRE -> displayed
        else -> (displayed * KM_PER_MILE).roundToInt()
    }
    return of(km)
}
