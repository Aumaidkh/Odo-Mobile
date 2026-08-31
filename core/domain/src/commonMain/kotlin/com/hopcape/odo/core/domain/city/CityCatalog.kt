package com.hopcape.odo.core.domain.city

/**
 * Read-only reference data for the city picker.
 *
 * A domain **port** so Presentation depends only on `:core:domain` and never hardcodes a city
 * list — the data layer backs it with a table synced from Supabase, not a seeded local one:
 * unlike [com.hopcape.odo.core.domain.car.catalog.VehicleCatalog], there is no bundled seed to
 * fall back on, since a city has no per-app-release list to ship in the first place.
 */
interface CityCatalog {

    /** Every active city, in display order. */
    suspend fun cities(): List<City>
}

/** One selectable city, as read back from the local synced cache. */
data class City(
    val id: String,
    val name: String,
    val state: String,
    val tier: Int,
)
