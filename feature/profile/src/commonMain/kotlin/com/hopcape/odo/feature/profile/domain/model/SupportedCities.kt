package com.hopcape.odo.feature.profile.domain.model

/**
 * The cities the profile offers.
 *
 * A constant, not a lookup: the server's `cities` table is not mirrored locally yet, and
 * these six are exactly the ones Odo has data for — the fuel-price seed is keyed on them,
 * and a city outside this list produces no fuel estimate and no benchmark. Offering a
 * seventh would let an owner pick a city that silently turns their figures off.
 *
 * Replaced by a read of the synced `cities` table when M5 lands.
 */
internal val SUPPORTED_CITIES: List<String> = listOf(
    "Pune",
    "Mumbai",
    "Delhi",
    "Bengaluru",
    "Chennai",
    "Hyderabad",
)
