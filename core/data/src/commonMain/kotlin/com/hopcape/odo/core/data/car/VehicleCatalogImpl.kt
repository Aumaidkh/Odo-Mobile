package com.hopcape.odo.core.data.car

import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.model.ModelYear

/**
 * [VehicleCatalog] backed by the seeded `vehicle_make`/`vehicle_model` tables.
 * Years and fuel types come straight from the domain types so every dropdown has
 * one source of truth.
 */
internal class VehicleCatalogImpl(
    private val database: OdoDatabase,
) : VehicleCatalog {

    override suspend fun makes(): List<String> =
        database.vehicleMakeQueries.selectAllMakes().executeAsList()

    override suspend fun models(make: String): List<String> =
        database.vehicleModelQueries.selectModelsByMakeName(make).executeAsList()

    override fun years(): List<Int> = ModelYear.RANGE.reversed().toList()

    override fun fuelTypes(): List<FuelType> = FuelType.entries.toList()
}
