package com.hopcape.odo.infrastructure.database.car

import com.hopcape.odo.core.data.car.CarDto
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.infrastructure.database.sync.SyncRunner

/**
 * `CarRepositoryImpl`'s sync half, split out because the SQLDelight-backed [SyncRunner] it
 * wraps cannot live in `:core:data` without a dependency cycle — `SyncRunner` and
 * `CarSyncTable` both live here, next to the database they need.
 */
internal class CarSyncable(
    private val runner: SyncRunner<CarDto>,
) : Syncable {

    override val entity: SyncEntity = SyncEntity.CARS

    override suspend fun pushTo(synchronizer: Synchronizer): Boolean = runner.push(synchronizer)

    override suspend fun pullFrom(synchronizer: Synchronizer): Boolean = runner.pull(synchronizer)
}
