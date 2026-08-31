package com.hopcape.odo.infrastructure.database.car

import com.hopcape.odo.core.data.car.VehicleCatalogRemoteDataSource
import com.hopcape.odo.core.data.car.VehicleCatalogSubmissionDto
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.db.Vehicle_catalog_submissions
import com.hopcape.odo.infrastructure.database.sync.FetchResult
import com.hopcape.odo.infrastructure.database.sync.LocalRowState
import com.hopcape.odo.infrastructure.database.sync.SyncTable
import com.hopcape.odo.infrastructure.database.sync.toInstantOrNull
import com.hopcape.odo.infrastructure.database.sync.toSyncStatus
import kotlin.time.Instant

/**
 * `vehicle_catalog_submissions` as the sync algorithm sees it — **push only**.
 *
 * [fetch] deliberately answers with nothing, the same way [com.hopcape.odo.infrastructure
 * .database.fairness.OverchargeReportSyncTable] does: a report is something the owner filed
 * once, it has no server-side lifecycle, and no screen reads it back. The port has no
 * `fetchSince` for the same reason.
 */
internal class VehicleCatalogSubmissionSyncTable(
    private val database: OdoDatabase,
    private val remote: VehicleCatalogRemoteDataSource,
) : SyncTable<VehicleCatalogSubmissionDto> {

    private val queries get() = database.vehicleCatalogSubmissionQueries

    override fun idOf(dto: VehicleCatalogSubmissionDto): String = dto.id

    override fun updatedAtOf(dto: VehicleCatalogSubmissionDto): Instant? = dto.createdAt.toInstantOrNull()

    override suspend fun pending(): List<VehicleCatalogSubmissionDto> =
        queries.selectPending().executeAsList().map(Vehicle_catalog_submissions::toDto)

    override suspend fun push(rows: List<VehicleCatalogSubmissionDto>): List<VehicleCatalogSubmissionDto> =
        remote.push(rows)

    override fun markSynced(id: String, remoteVersion: String) =
        queries.markSynced(remoteVersion = remoteVersion, id = id)

    override fun markConflict(id: String) = queries.markConflict(id)

    /** Nothing to pull — see the class note. */
    override suspend fun fetch(since: Instant?): FetchResult<VehicleCatalogSubmissionDto> =
        FetchResult.Rows(emptyList())

    override fun localState(id: String): LocalRowState? =
        queries.selectSyncState(id).executeAsOneOrNull()?.let { row ->
            LocalRowState(row.sync_status.toSyncStatus(), row.updated_at.toInstantOrNull())
        }

    /** Unreachable while [fetch] is empty, but the interface requires an implementation. */
    override fun applyRemote(dto: VehicleCatalogSubmissionDto) = Unit
}

private fun Vehicle_catalog_submissions.toDto() = VehicleCatalogSubmissionDto(
    id = id,
    ownerId = owner_id,
    make = make,
    model = model,
    variant = variant,
    createdAt = created_at,
)
