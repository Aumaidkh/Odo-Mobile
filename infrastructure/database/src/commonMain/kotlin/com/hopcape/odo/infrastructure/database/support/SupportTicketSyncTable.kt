package com.hopcape.odo.infrastructure.database.support

import com.hopcape.odo.core.data.support.SupportTicketDto
import com.hopcape.odo.core.data.support.SupportTicketRemoteDataSource
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.db.Support_tickets
import com.hopcape.odo.infrastructure.database.sync.FetchResult
import com.hopcape.odo.infrastructure.database.sync.LocalRowState
import com.hopcape.odo.infrastructure.database.sync.SyncTable
import com.hopcape.odo.infrastructure.database.sync.toInstantOrNull
import com.hopcape.odo.infrastructure.database.sync.toSyncStatus
import kotlin.time.Instant

/**
 * `support_tickets` as the sync algorithm sees it — push **and** pull.
 *
 * Unlike the other submission tables, a ticket has a life after it is filed: the panel sets a
 * status on it, and an owner who reported something is owed the answer to whether it was
 * looked at. So the pull is real, and `status` is the column it exists for — this device never
 * writes anything but the opening value.
 */
internal class SupportTicketSyncTable(
    private val database: OdoDatabase,
    private val remote: SupportTicketRemoteDataSource,
    private val currentOwner: CurrentOwnerProvider,
) : SyncTable<SupportTicketDto> {

    private val queries get() = database.supportTicketQueries

    override fun idOf(dto: SupportTicketDto): String = dto.clientId

    override fun updatedAtOf(dto: SupportTicketDto): Instant? = dto.updatedAt.toInstantOrNull()

    override suspend fun pending(): List<SupportTicketDto> =
        queries.selectPending().executeAsList().map(Support_tickets::toDto)

    override suspend fun push(rows: List<SupportTicketDto>): List<SupportTicketDto> =
        remote.push(rows)

    override fun markSynced(id: String, remoteVersion: String) =
        queries.markSynced(remoteVersion = remoteVersion, id = id)

    override fun markConflict(id: String) = queries.markConflict(id)

    override suspend fun fetch(since: Instant?): FetchResult<SupportTicketDto> =
        FetchResult.Rows(
            remote.fetch(
                ownerId = currentOwner.currentOwnerId().value,
                since = since?.toString(),
            ),
        )

    override fun localState(id: String): LocalRowState? =
        queries.selectSyncState(id).executeAsOneOrNull()?.let { row ->
            LocalRowState(row.sync_status.toSyncStatus(), row.updated_at.toInstantOrNull())
        }

    override fun applyRemote(dto: SupportTicketDto) {
        queries.upsertFromRemote(
            id = dto.clientId,
            ownerId = dto.ownerId,
            kind = dto.kind,
            body = dto.body,
            details = dto.details,
            attachments = dto.attachments,
            replyTo = dto.replyTo,
            diagnosticsReference = dto.diagnosticsReference,
            status = dto.status,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            deletedAt = dto.deletedAt,
            remoteVersion = dto.updatedAt,
        )
    }
}

private fun Support_tickets.toDto() = SupportTicketDto(
    clientId = id,
    ownerId = owner_id,
    kind = kind,
    body = body,
    details = details,
    attachments = attachments,
    replyTo = reply_to,
    diagnosticsReference = diagnostics_reference,
    status = status,
    createdAt = created_at,
    updatedAt = updated_at,
    deletedAt = deleted_at,
)
