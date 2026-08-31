package com.hopcape.odo.infrastructure.database.city

import com.hopcape.odo.core.domain.city.City
import com.hopcape.odo.core.domain.city.CityCatalog
import com.hopcape.odo.infrastructure.database.db.OdoDatabase

/** [CityCatalog] backed by the synced `city` table. */
internal class CityCatalogImpl(
    private val database: OdoDatabase,
) : CityCatalog {

    override suspend fun cities(): List<City> =
        database.cityQueries.selectActive().executeAsList().map { row ->
            City(id = row.id, name = row.name, state = row.state, tier = row.tier.toInt())
        }
}
