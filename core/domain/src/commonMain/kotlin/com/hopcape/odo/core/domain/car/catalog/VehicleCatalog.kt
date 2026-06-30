package com.hopcape.odo.core.domain.car.catalog

import com.hopcape.odo.core.domain.car.model.FuelType

/**
 * Read-only reference data for the car-onboarding dropdowns.
 *
 * A domain **port** so Presentation depends only on `:core:domain` and never
 * hardcodes brand/model lists — the data layer backs makes/models with a seeded
 * local table. Years and fuel types are derived from the domain types
 * themselves, so there is a single source for every dropdown.
 */
interface VehicleCatalog {

    /** Selectable car makes (brands), in display order. */
    suspend fun makes(): List<String>

    /** Selectable models for [make], in display order; empty if the make is unknown. */
    suspend fun models(make: String): List<String>

    /** Selectable manufacturing years, newest first. */
    fun years(): List<Int>

    /** Selectable fuel types. */
    fun fuelTypes(): List<FuelType>
}
