package com.hopcape.odo.core.domain.shared

/**
 * Kilometre rendering for [Distance] — the one place a distance becomes a display
 * string, shared across features (odometer readings, distance driven). Indian digit
 * grouping matches [Amount.formatRupees].
 */

/** "22,200 km" — Indian digit grouping. */
fun Distance.formatKm(): String = "${groupIndianDigits(km.toLong())} km"
