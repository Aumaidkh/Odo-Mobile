package com.hopcape.odo.infrastructure.database.owner

import com.hopcape.odo.core.data.owner.ProfileDto
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.infrastructure.database.sync.SyncRunner

/**
 * `OwnerProfileRepositoryImpl`'s sync half, split out because the SQLDelight-backed
 * [SyncRunner] it wraps cannot live in `:core:data` without a dependency cycle —
 * `SyncRunner` and `ProfileSyncTable` both live here, next to the database they need.
 */
internal class ProfileSyncable(
    private val runner: SyncRunner<ProfileDto>,
) : Syncable {

    override val entity: SyncEntity = SyncEntity.PROFILES

    override suspend fun pushTo(synchronizer: Synchronizer): Boolean = runner.push(synchronizer)

    override suspend fun pullFrom(synchronizer: Synchronizer): Boolean = runner.pull(synchronizer)
}
