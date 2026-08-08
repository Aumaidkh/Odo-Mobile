package com.hopcape.odo.infrastructure.database.trip

import com.hopcape.odo.core.data.trip.TripDto
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.infrastructure.database.sync.SyncRunner

/**
 * `TripRepositoryImpl`'s sync half, split out because the SQLDelight-backed [SyncRunner]
 * it wraps cannot live in `:core:data` without a dependency cycle — `SyncRunner` and
 * `TripSyncTable` both live here, next to the database they need.
 */
internal class TripSyncable(
    private val runner: SyncRunner<TripDto>,
) : Syncable {

    override val entity: SyncEntity = SyncEntity.TRIPS

    override suspend fun syncWith(synchronizer: Synchronizer): Boolean = runner.run(synchronizer)
}
