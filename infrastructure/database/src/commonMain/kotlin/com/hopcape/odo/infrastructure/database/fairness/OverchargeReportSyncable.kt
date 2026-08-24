package com.hopcape.odo.infrastructure.database.fairness

import com.hopcape.odo.core.data.fairness.OverchargeReportDto
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.infrastructure.database.sync.SyncRunner

/**
 * `OverchargeReportRepositoryImpl`'s sync half, split out because the SQLDelight-backed
 * [SyncRunner] it wraps cannot live in `:core:data` without a dependency cycle —
 * `SyncRunner` and `OverchargeReportSyncTable` both live here, next to the database they
 * need.
 */
internal class OverchargeReportSyncable(
    private val runner: SyncRunner<OverchargeReportDto>,
) : Syncable {

    override val entity: SyncEntity = SyncEntity.OVERCHARGE_REPORTS

    override suspend fun pushTo(synchronizer: Synchronizer): Boolean = runner.push(synchronizer)

    override suspend fun pullFrom(synchronizer: Synchronizer): Boolean = runner.pull(synchronizer)
}
