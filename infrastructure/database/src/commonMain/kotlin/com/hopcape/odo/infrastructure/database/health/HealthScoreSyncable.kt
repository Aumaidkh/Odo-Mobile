package com.hopcape.odo.infrastructure.database.health

import com.hopcape.odo.core.data.health.HealthScoreDto
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.infrastructure.database.sync.SyncRunner

/**
 * `HealthScoreRepositoryImpl`'s sync half, split out because the SQLDelight-backed
 * [SyncRunner] it wraps cannot live in `:core:data` without a dependency cycle —
 * `SyncRunner` and `HealthScoreSyncTable` both live here, next to the database they need.
 */
internal class HealthScoreSyncable(
    private val runner: SyncRunner<HealthScoreDto>,
) : Syncable {

    override val entity: SyncEntity = SyncEntity.HEALTH_SCORES

    override suspend fun syncWith(synchronizer: Synchronizer): Boolean = runner.run(synchronizer)
}
