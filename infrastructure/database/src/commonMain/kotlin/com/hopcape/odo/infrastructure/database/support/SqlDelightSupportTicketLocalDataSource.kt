package com.hopcape.odo.infrastructure.database.support

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hopcape.odo.core.data.support.SupportTicketLocalDataSource
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.support.SupportTicket
import com.hopcape.odo.core.domain.support.SupportTicketId
import com.hopcape.odo.core.domain.support.TicketAttachment
import com.hopcape.odo.core.domain.support.TicketKind
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.db.Support_tickets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * Tickets in SQLDelight.
 *
 * The two collection columns are JSON. A ticket's details differ per kind and its attachments
 * are a list, and neither is ever queried *into* — the panel filters on `kind` and reads the
 * rest whole — so a column each beats two more tables and the joins that come with them.
 *
 * A row whose JSON cannot be read comes back with that part empty rather than taking the
 * ticket with it. The body is the report; losing the details is a worse screen, and losing
 * the row is a lost report.
 */
internal class SqlDelightSupportTicketLocalDataSource(
    private val database: OdoDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SupportTicketLocalDataSource {

    private val queries get() = database.supportTicketQueries

    override suspend fun insert(ownerId: OwnerId, ticket: SupportTicket) {
        queries.insertTicket(
            id = ticket.id.value,
            ownerId = ownerId.value,
            kind = ticket.kind.name,
            body = ticket.body,
            details = json.encodeToString(ticket.details),
            attachments = json.encodeToString(ticket.attachments.map { it.toStored() }),
            replyTo = ticket.replyTo,
            diagnosticsReference = ticket.diagnosticsReference,
            status = STATUS_OPEN,
            createdAt = ticket.createdAt.toString(),
        )
    }

    override fun observe(ownerId: OwnerId): Flow<List<SupportTicket>> =
        queries.selectAll(ownerId.value)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    private fun Support_tickets.toDomain() = SupportTicket(
        id = SupportTicketId(id),
        // A kind this build does not know is a row written by a newer one. Reading it as a
        // problem report keeps it visible instead of dropping it.
        kind = TicketKind.entries.firstOrNull { it.name == kind } ?: TicketKind.PROBLEM,
        body = body,
        details = decode(details) { json.decodeFromString<Map<String, String>>(it) }.orEmpty(),
        attachments = decode(attachments) {
            json.decodeFromString<List<StoredAttachment>>(it)
        }.orEmpty().map { it.toDomain() },
        replyTo = reply_to,
        diagnosticsReference = diagnostics_reference,
        createdAt = Instant.parse(created_at),
    )

    /** JSON that cannot be read costs that column, never the row. */
    private fun <T> decode(raw: String, block: (String) -> T): T? =
        runCatching { block(raw) }.getOrNull()

    private companion object {
        /**
         * Lowercase, matching the server's own check constraint and the value its column
         * already defaults to. Sending "OPEN" would be a `23514` on every push — and a 400 is
         * permanent, so the row would park in CONFLICT and never retry.
         */
        const val STATUS_OPEN = "open"

        val json = Json { ignoreUnknownKeys = true }
    }
}
