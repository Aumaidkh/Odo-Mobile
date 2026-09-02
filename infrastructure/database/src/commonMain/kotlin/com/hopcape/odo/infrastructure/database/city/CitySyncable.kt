package com.hopcape.odo.infrastructure.database.city

import com.hopcape.odo.core.data.city.CityDto
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.infrastructure.database.sync.SyncRunner

/**
 * `CityCatalogImpl`'s sync half, split out because the SQLDelight-backed [SyncRunner] it wraps
 * cannot live in `:core:data` without a dependency cycle — `SyncRunner` and [CitySyncTable]
 * both live here, next to the database they need.
 */
internal class CitySyncable(
    private val runner: SyncRunner<CityDto>,
) : Syncable {

    override val entity: SyncEntity = SyncEntity.CITIES

    override suspend fun pushTo(synchronizer: Synchronizer): Boolean = runner.push(synchronizer)

    override suspend fun pullFrom(synchronizer: Synchronizer): Boolean = runner.pull(synchronizer)
}
