package com.hopcape.odo.infrastructure.database.fairness

import com.hopcape.odo.core.data.fairness.OverchargeRemoteDataSource
import com.hopcape.odo.core.data.fairness.OverchargeReportDto
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.db.Overcharge_reports
import com.hopcape.odo.infrastructure.database.sync.FetchResult
import com.hopcape.odo.infrastructure.database.sync.LocalRowState
import com.hopcape.odo.infrastructure.database.sync.SyncTable
import com.hopcape.odo.infrastructure.database.sync.toInstantOrNull
import com.hopcape.odo.infrastructure.database.sync.toSyncStatus
import kotlin.time.Instant

/**
 * `overcharge_reports` as the sync algorithm sees it — **push only**.
 *
 * [fetch] deliberately answers with nothing, and that is not a stub. A report is something
 * the owner filed once; it has no server-side lifecycle and no screen reads it back, so
 * there is nothing a pull could tell this device that it does not already know. The port
 * has no `fetchSince` for the same reason.
 *
 * If reports ever gain a status the server sets — triaged, actioned — this is where the
 * pull goes, and the conflict rules already apply without change.
 *
 * The wire name for the category is `service_category`, not the local column's `category`.
 */
internal class OverchargeReportSyncTable(
    private val database: OdoDatabase,
    private val remote: OverchargeRemoteDataSource,
) : SyncTable<OverchargeReportDto> {

    private val queries get() = database.overchargeReportQueries

    override fun idOf(dto: OverchargeReportDto): String = dto.id

    override fun updatedAtOf(dto: OverchargeReportDto): Instant? = dto.createdAt.toInstantOrNull()

    override suspend fun pending(): List<OverchargeReportDto> =
        queries.selectPending().executeAsList().map(Overcharge_reports::toDto)

    override suspend fun push(rows: List<OverchargeReportDto>): List<OverchargeReportDto> =
        remote.push(rows)

    override fun markSynced(id: String, remoteVersion: String) =
        queries.markSynced(remoteVersion = remoteVersion, id = id)

    override fun markConflict(id: String) = queries.markConflict(id)

    /**
     * Nothing to pull — see the class note.
     *
     * An empty [FetchResult.Rows] rather than a missing scope: this table knows perfectly
     * well what it would ask for and has decided not to ask, which is a different thing from
     * being unable to.
     */
    override suspend fun fetch(since: Instant?): FetchResult<OverchargeReportDto> =
        FetchResult.Rows(emptyList())

    override fun localState(id: String): LocalRowState? =
        queries.selectSyncState(id).executeAsOneOrNull()?.let { row ->
            LocalRowState(row.sync_status.toSyncStatus(), row.updated_at.toInstantOrNull())
        }

    /** Unreachable while [fetch] is empty, but written out so a future pull is one line away. */
    override fun applyRemote(dto: OverchargeReportDto) {
        queries.insertFromRemote(
            id = dto.id,
            service_log_id = dto.serviceLogId,
            owner_id = dto.ownerId,
            reason = dto.reason.uppercase(),
            category = dto.category?.uppercase(),
            note = dto.note,
            created_at = dto.createdAt,
            updated_at = dto.createdAt,
            deleted_at = null,
            remote_version = dto.createdAt,
        )
        queries.updateFromRemote(
            service_log_id = dto.serviceLogId,
            owner_id = dto.ownerId,
            reason = dto.reason.uppercase(),
            category = dto.category?.uppercase(),
            note = dto.note,
            created_at = dto.createdAt,
            updated_at = dto.createdAt,
            deleted_at = null,
            remote_version = dto.createdAt,
            id = dto.id,
        )
    }
}

private fun Overcharge_reports.toDto() = OverchargeReportDto(
    id = id,
    serviceLogId = service_log_id,
    ownerId = owner_id,
    reason = reason.lowercase(),
    category = category?.lowercase(),
    note = note,
    createdAt = created_at,
)
