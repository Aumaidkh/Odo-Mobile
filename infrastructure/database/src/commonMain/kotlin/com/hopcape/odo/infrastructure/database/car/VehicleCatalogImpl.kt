package com.hopcape.odo.infrastructure.database.car

import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.core.domain.car.catalog.CarModel
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

    /**
     * The chips shown above the full brand list — the first [POPULAR_MAKE_COUNT] makes in
     * the same market-share ordering, so there is no second definition of "popular".
     */
    override suspend fun popularMakes(): List<String> =
        database.vehicleMakeQueries.selectPopularMakes(POPULAR_MAKE_COUNT).executeAsList()

    override suspend fun models(make: String): List<CarModel> =
        database.vehicleModelQueries
            .selectModelsByMakeName(make)
            .executeAsList()
            .map { row -> CarModel(name = row.name, variant = row.variant) }

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

    private companion object {
        /** How many brands get a one-tap chip. Four fits a row without wrapping. */
        const val POPULAR_MAKE_COUNT = 4L
    }
}
