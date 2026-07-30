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
     * The handful of brands offered as one-tap chips above the full list.
     *
     * A prefix of [makes] rather than a separately curated set: the catalog is already
     * ordered by market share, so "popular" and "listed first" are the same fact — and
     * keeping one ordering means they can never disagree.
     */
    suspend fun popularMakes(): List<String>

    /** Selectable models (with trims) for [make], in display order; empty if the make is unknown. */
    suspend fun models(make: String): List<CarModel>

    /** Selectable manufacturing years, newest first. */
    fun years(): List<Int>

    /** Selectable fuel types. */
    fun fuelTypes(): List<FuelType>
}
