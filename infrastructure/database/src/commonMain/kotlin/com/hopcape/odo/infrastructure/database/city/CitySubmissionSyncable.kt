package com.hopcape.odo.infrastructure.database.city

import com.hopcape.odo.core.data.city.CitySubmissionDto
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.infrastructure.database.sync.SyncRunner

/**
 * `UnlistedCityReporterImpl`'s sync half, split out because the SQLDelight-backed [SyncRunner]
 * it wraps cannot live in `:core:data` without a dependency cycle — `SyncRunner` and
 * [CitySubmissionSyncTable] both live here, next to the database they need.
 */
internal class CitySubmissionSyncable(
    private val runner: SyncRunner<CitySubmissionDto>,
) : Syncable {

    override val entity: SyncEntity = SyncEntity.CITY_SUBMISSIONS

    override suspend fun pushTo(synchronizer: Synchronizer): Boolean = runner.push(synchronizer)

    override suspend fun pullFrom(synchronizer: Synchronizer): Boolean = runner.pull(synchronizer)
}
