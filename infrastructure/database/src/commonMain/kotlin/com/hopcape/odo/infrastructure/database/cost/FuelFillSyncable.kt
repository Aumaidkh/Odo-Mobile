package com.hopcape.odo.infrastructure.database.cost

import com.hopcape.odo.core.data.cost.FuelFillDto
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.infrastructure.database.sync.SyncRunner

/**
 * `FuelFillRepositoryImpl`'s sync half, split out because the SQLDelight-backed [SyncRunner]
 * it wraps cannot live in `:core:data` without a dependency cycle — `SyncRunner` and
 * [FuelFillSyncTable] both live here, next to the database they need.
 */
internal class FuelFillSyncable(
    private val runner: SyncRunner<FuelFillDto>,
) : Syncable {

    override val entity: SyncEntity = SyncEntity.FUEL_FILLS

    override suspend fun pushTo(synchronizer: Synchronizer): Boolean = runner.push(synchronizer)

    override suspend fun pullFrom(synchronizer: Synchronizer): Boolean = runner.pull(synchronizer)
}
