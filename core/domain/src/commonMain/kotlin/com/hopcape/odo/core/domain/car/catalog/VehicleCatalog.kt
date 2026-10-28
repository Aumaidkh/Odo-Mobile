package com.hopcape.odo.core.domain.car.catalog

import com.hopcape.odo.core.domain.car.model.FuelType

/**
 * Read-only reference data for the car-onboarding pickers.
 *
 * A domain **port** so Presentation depends only on `:core:domain` and never
 * hardcodes brand/model lists — the data layer backs makes/models with a seeded
 * local table. Years and fuel types are derived from the domain types
 * themselves, so there is a single source for every picker.
 */
interface VehicleCatalog {

    /** Selectable car makes (brands), in display order. */
    suspend fun makes(): List<String>

    /**
     * The first [limit] brands of [makes], offered as one-tap choices.
     *
     * A prefix rather than a curated set: the catalog is ordered by market share, so
     * "popular" and "listed first" are the same fact.
     */
    suspend fun popularMakes(limit: Int): List<String>

    /**
     * Logo locations by make name, as Storage paths. Makes without a logo are absent.
     * Defaulted so catalogs that have no logos need not implement it.
     */
    suspend fun makeLogoPaths(): Map<String, String> = emptyMap()

    /** Selectable models (with trims) for [make], in display order; empty if the make is unknown. */
    suspend fun models(make: String): List<CarModel>

    /** Selectable manufacturing years, newest first. */
    fun years(): List<Int>

    /** Selectable fuel types. */
    fun fuelTypes(): List<FuelType>
}
