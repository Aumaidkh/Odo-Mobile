package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.support.FeatureIdeaDto
import com.hopcape.odo.core.data.support.FeatureIdeaRemoteDataSource
import com.hopcape.odo.core.data.support.IdeaVoteDto
import com.hopcape.odo.core.data.support.IdeaVoteRemoteDataSource
import com.hopcape.odo.core.data.support.SupportTicketDto
import com.hopcape.odo.core.data.support.SupportTicketRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.Serializable

/**
 * `support_tickets` over PostgREST.
 *
 * **Conflicts resolve on `client_id`, not the primary key.** The table's own `id` is a bigint
 * the server assigns, so a device has no way to name a row by it — `client_id` is the column
 * the app generated and the one a re-push has to match, or the second attempt inserts a
 * duplicate ticket instead of updating the first.
 *
 * `details` and `attachments` travel as the JSON strings the local columns hold. Nothing
 * between here and the panel needs to read into them, and re-encoding a structure twice is two
 * places for its shape to drift.
 */
internal class SupabaseSupportTicketRemoteDataSource(
    private val postgrest: PostgrestClient,
) : SupportTicketRemoteDataSource {

    override suspend fun push(rows: List<SupportTicketDto>): List<SupportTicketDto> =
        postgrest.upsert(
            table = TABLE,
            serializer = TicketRow.serializer(),
            rows = rows.map { it.toRow() },
            onConflict = CONFLICT_COLUMN,
        ).map { it.toDto() }

    override suspend fun fetch(ownerId: String, since: String?): List<SupportTicketDto> =
        postgrest.select(
            table = TABLE,
            serializer = TicketRow.serializer(),
            filters = buildMap {
                put(COLUMN_OWNER, "eq.$ownerId")
                // Only rows this app named. The panel can create a ticket itself and the
                // eleven that predate this feature have no client_id, and a row the local
                // table has no key for is a row the sync cannot place.
                put(CONFLICT_COLUMN, "not.is.null")
                // The delta the sync cursor asks for. Absent on a first pull, which is what
                // fetches everything this owner has ever filed.
                since?.let { put(COLUMN_UPDATED_AT, "gt.$it") }
            },
            order = "$COLUMN_UPDATED_AT.asc",
        ).map { it.toDto() }

    private companion object {
        const val TABLE = "support_tickets"

        /** The app's own id. The table's `id` is a bigint the server assigns. */
        const val CONFLICT_COLUMN = "client_id"
        const val COLUMN_OWNER = "owner_id"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}

/**
 * `idea_votes` over PostgREST.
 *
 * The conflict target is the pair, matching the local table's key: pressing the pill twice on
 * two devices has to end as one row, not two.
 */
internal class SupabaseIdeaVoteRemoteDataSource(
    private val postgrest: PostgrestClient,
) : IdeaVoteRemoteDataSource {

    override suspend fun push(rows: List<IdeaVoteDto>): List<IdeaVoteDto> =
        postgrest.upsert(
            table = TABLE,
            serializer = VoteRow.serializer(),
            rows = rows.map { it.toRow() },
            onConflict = CONFLICT_COLUMNS,
        ).map { it.toDto() }

    override suspend fun fetch(ownerId: String, since: String?): List<IdeaVoteDto> =
        postgrest.select(
            table = TABLE,
            serializer = VoteRow.serializer(),
            filters = buildMap {
                put(COLUMN_OWNER, "eq.$ownerId")
                since?.let { put(COLUMN_UPDATED_AT, "gt.$it") }
            },
            order = "$COLUMN_UPDATED_AT.asc",
        ).map { it.toDto() }

    private companion object {
        const val TABLE = "idea_votes"
        const val CONFLICT_COLUMNS = "idea_id,owner_id"
        const val COLUMN_OWNER = "owner_id"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}

/**
 * `feature_ideas` over PostgREST — read only.
 *
 * No owner filter: the catalogue is the same list for everybody, and the vote count on it is
 * the server's own tally.
 */
internal class SupabaseFeatureIdeaRemoteDataSource(
    private val postgrest: PostgrestClient,
) : FeatureIdeaRemoteDataSource {

    override suspend fun ideas(): List<FeatureIdeaDto> =
        postgrest.select(
            table = TABLE,
            serializer = IdeaRow.serializer(),
            filters = mapOf(COLUMN_DELETED_AT to "is.null"),
            order = "$COLUMN_VOTES.desc",
        ).map { it.toDto() }

    private companion object {
        const val TABLE = "feature_ideas"
        const val COLUMN_DELETED_AT = "deleted_at"
        const val COLUMN_VOTES = "votes"
    }
}

/* ------------------------------ Wire shapes ------------------------------ */

/**
 * Nulls are written out rather than omitted.
 *
 * PostgREST refuses a batch whose objects have different key sets (`PGRST102`), and a column
 * an owner cleared would never reach the server if the key were simply left out.
 */
@Serializable
private data class TicketRow(
    /**
     * Nullable, because rows filed before the app could write here have none — the eleven
     * already in the table, and anything the panel creates itself. Decoding those into a
     * non-null field threw, which failed the whole pull for any owner who had one.
     */
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("kind") val kind: String,
    @SerialName("body") val body: String,
    /**
     * A real JSON object, not a string holding one.
     *
     * The column is `jsonb`. Sending the text `"{\"area\":\"REMINDERS\"}"` stores a jsonb
     * *scalar string*, and the panel's `details->>'area'` then answers null — which is the
     * whole reason the column exists.
     */
    @SerialName("details") val details: JsonElement,
    @SerialName("attachments") val attachments: JsonElement,
    @SerialName("diagnostics_reference") val diagnosticsReference: String? = null,
    @SerialName("status") val status: String,
    /**
     * Where the answer goes — the queue's own column for it, which predates this feature.
     *
     * The local table calls the same thing `reply_to`. Only one of them travels: a second
     * server column holding the same address is a second thing to keep in step, and the
     * panel's reply already reads this one.
     */
    @SerialName("contact") val contact: String,
    @SerialName("subject") val subject: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class VoteRow(
    @SerialName("idea_id") val ideaId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class IdeaRow(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("status") val status: String,
    @SerialName("votes") val votes: Long = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

private fun SupportTicketDto.toRow() = TicketRow(
    clientId = clientId,
    ownerId = ownerId,
    kind = kind,
    body = body,
    details = details.asJson(EMPTY_OBJECT),
    attachments = attachments.asJson(EMPTY_ARRAY),
    // What the panel's list draws. Assembled here rather than stored twice: it is a label for
    // a queue, and a column holding a copy of what `kind` and `details` already say is a
    // column that can disagree with them.
    contact = replyTo.orEmpty(),
    subject = subject(),
    diagnosticsReference = diagnosticsReference,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

private fun TicketRow.toDto() = SupportTicketDto(
    // Only rows the app named reach here — the fetch filters the rest out server-side.
    clientId = clientId.orEmpty(),
    ownerId = ownerId,
    kind = kind,
    body = body,
    details = details.toString(),
    attachments = attachments.toString(),
    // The server's column for it. Blank means the ticket was filed with nowhere to reply.
    replyTo = contact.takeIf { it.isNotBlank() },
    diagnosticsReference = diagnosticsReference,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

/**
 * The line the panel's queue shows.
 *
 * The kind, and the one detail that says what it is about — the area for a report, the job for
 * a correction. An idea has neither and is named by its kind alone.
 */
private fun SupportTicketDto.subject(): String {
    val about = details.asJson(EMPTY_OBJECT)
        .let { it as? JsonObject }
        ?.let { (it[DETAIL_AREA] ?: it[DETAIL_JOB]) as? JsonPrimitive }
        ?.content
    val label = kind.lowercase().replace('_', ' ')
    return if (about.isNullOrBlank()) label else "$label: $about"
}

/**
 * The column's text as JSON, or an empty value when it cannot be read.
 *
 * The local column holds a JSON string this never has to look into; parsing it here is only so
 * the server stores an object rather than a string containing one. Unparseable text becomes
 * empty rather than failing the push: the body is the report, and the details are a filter.
 */
private fun String.asJson(fallback: JsonElement): JsonElement =
    runCatching { Json.parseToJsonElement(this) }.getOrDefault(fallback)

private val EMPTY_OBJECT = JsonObject(emptyMap())
private val EMPTY_ARRAY = JsonArray(emptyList())
private const val DETAIL_AREA = "area"
private const val DETAIL_JOB = "job"

private fun IdeaVoteDto.toRow() = VoteRow(
    ideaId = ideaId,
    ownerId = ownerId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

private fun VoteRow.toDto() = IdeaVoteDto(
    ideaId = ideaId,
    ownerId = ownerId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

private fun IdeaRow.toDto() = FeatureIdeaDto(
    id = id,
    title = title,
    status = status,
    votes = votes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
