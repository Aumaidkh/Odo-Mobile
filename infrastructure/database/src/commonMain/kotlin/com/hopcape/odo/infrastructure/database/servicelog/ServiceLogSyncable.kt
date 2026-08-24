package com.hopcape.odo.infrastructure.database.servicelog

import com.hopcape.odo.core.data.servicelog.ServiceLogDto
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.infrastructure.database.sync.SyncRunner

/**
 * `ServiceLogRepositoryImpl`'s sync half, split out because the SQLDelight-backed
 * [SyncRunner] it wraps cannot live in `:core:data` without a dependency cycle —
 * `SyncRunner` and `ServiceLogSyncTable` both live here, next to the database they need.
 */
internal class ServiceLogSyncable(
    private val runner: SyncRunner<ServiceLogDto>,
) : Syncable {

    override val entity: SyncEntity = SyncEntity.SERVICE_LOGS

    override suspend fun pushTo(synchronizer: Synchronizer): Boolean = runner.push(synchronizer)

    override suspend fun pullFrom(synchronizer: Synchronizer): Boolean = runner.pull(synchronizer)
}
