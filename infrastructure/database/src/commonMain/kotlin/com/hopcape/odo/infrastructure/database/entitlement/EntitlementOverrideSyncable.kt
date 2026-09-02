package com.hopcape.odo.infrastructure.database.entitlement

import com.hopcape.odo.core.data.entitlement.EntitlementOverrideDto
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.infrastructure.database.sync.SyncRunner

/**
 * The sync half of the entitlement overrides, split out for the same reason
 * [com.hopcape.odo.infrastructure.database.city.CitySyncable] is: the SQLDelight-backed
 * [SyncRunner] cannot live in `:core:data` without a dependency cycle.
 */
internal class EntitlementOverrideSyncable(
    private val runner: SyncRunner<EntitlementOverrideDto>,
) : Syncable {

    override val entity: SyncEntity = SyncEntity.ENTITLEMENT_OVERRIDES

    override suspend fun pushTo(synchronizer: Synchronizer): Boolean = runner.push(synchronizer)

    override suspend fun pullFrom(synchronizer: Synchronizer): Boolean = runner.pull(synchronizer)
}
