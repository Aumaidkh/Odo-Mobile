package com.hopcape.odo.core.data.car

import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.model.ModelYear

/**
 * [VehicleCatalog] backed by the seeded `vehicle_make`/`vehicle_model` tables.
 * Years and fuel types come straight from the domain types so every picker has
 * one source of truth.
 */
internal class VehicleCatalogImpl(
    private val database: OdoDatabase,
) : VehicleCatalog {

    override suspend fun makes(): List<String> =
        database.vehicleMakeQueries.selectAllMakes().executeAsList()

    override suspend fun models(make: String): List<String> =
        database.vehicleModelQueries.selectModelsByMakeName(make).executeAsList()

    /**
     * Selectable years, newest first — capped at the **current** year so a car's
     * model/purchase year can never be set in the future (the DB CHECK
     * [ModelYear.RANGE] stays lenient up to 2100; the UX must not offer future dates).
     */
    override fun years(): List<Int> {
        val newest = minOf(currentYear(), ModelYear.RANGE.last)
        return (newest downTo ModelYear.RANGE.first).toList()
    }

    override fun fuelTypes(): List<FuelType> = FuelType.entries.toList()
}
