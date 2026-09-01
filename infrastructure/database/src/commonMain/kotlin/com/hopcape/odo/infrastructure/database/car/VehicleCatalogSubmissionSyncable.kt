package com.hopcape.odo.infrastructure.database.car

import com.hopcape.odo.core.data.car.VehicleCatalogSubmissionDto
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.infrastructure.database.sync.SyncRunner

/**
 * `UnlistedVehicleReporterImpl`'s sync half, split out because the SQLDelight-backed
 * [SyncRunner] it wraps cannot live in `:core:data` without a dependency cycle —
 * `SyncRunner` and [VehicleCatalogSubmissionSyncTable] both live here, next to the database
 * they need.
 */
internal class VehicleCatalogSubmissionSyncable(
    private val runner: SyncRunner<VehicleCatalogSubmissionDto>,
) : Syncable {

    override val entity: SyncEntity = SyncEntity.VEHICLE_CATALOG_SUBMISSIONS

    override suspend fun pushTo(synchronizer: Synchronizer): Boolean = runner.push(synchronizer)

    override suspend fun pullFrom(synchronizer: Synchronizer): Boolean = runner.pull(synchronizer)
}
