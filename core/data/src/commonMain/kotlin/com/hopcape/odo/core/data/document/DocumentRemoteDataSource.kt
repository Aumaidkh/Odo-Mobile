package com.hopcape.odo.core.data.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The server side of the document vault, as the repository needs it: push what changed
 * locally, pull what changed remotely.
 *
 * A port, declared here in `:core:data` because this is the layer that knows how a row
 * becomes a payload. `:core:network` does not exist yet; when it does, it implements this
 * and the fake below is deleted — the repository does not change either way.
 *
 * Both halves are shaped for the sync engine (SYNC_DESIGN §6): [fetchSince] is the delta
 * pull keyed on a cursor, [push] is the outbox drain that returns what the server stored so
 * the local rows can take their `remote_version` and go SYNCED.
 *
 * The *file* behind a document is not this port's business. Rows sync here; bytes upload to
 * the `documents` bucket, which is a separate M5 job keyed on the same storage path.
 */
interface DocumentRemoteDataSource {

    /**
     * Everything on the account changed since [since] (null = never synced, so everything).
     *
     * Scoped to [ownerId] rather than to one car. A car id is not knowable at the moment a
     * pull runs — the cars themselves may only have arrived seconds earlier in the same run —
     * and scoping to one car also meant a second car's rows never arrived at all (issue
     * #312). `owner_id` is on every row and is what row-level security filters on anyway.
     */
    suspend fun fetchSince(ownerId: String, since: Instant?): List<DocumentDto>

    /** Send local changes; the returned rows are the server's accepted versions. */
    suspend fun push(documents: List<DocumentDto>): List<DocumentDto>
}

/**
 * The wire shape of a document — snake_case to match the Postgres columns (DB_SCHEMA §9.7),
 * so the same DTO serves the Supabase REST payload without a second mapping.
 *
 * [docType] and [docSource] carry the Postgres enum labels, which are lowercase
 * (`insurance`, `digilocker`) while the local column stores the Kotlin constant name. The
 * two are converted where rows become DTOs, which lands with the engine — the DTO stays a
 * faithful picture of the server row.
 */
@Serializable
data class DocumentDto(
    @SerialName("id") val id: String,
    @SerialName("car_id") val carId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("doc_type") val docType: String,
    @SerialName("title") val title: String? = null,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("doc_source") val docSource: String,
    @SerialName("issued_date") val issuedDate: String? = null,
    @SerialName("expiry_date") val expiryDate: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/**
 * Stand-in until `:core:network` exists: accepts pushes, remembers nothing, and has nothing
 * to pull.
 *
 * It returns the pushed rows back unchanged, which is honest about what it is — a server
 * that agrees with everything. It deliberately does **not** fabricate a `remote_version`:
 * inventing one would let local rows flip to SYNCED against a server that never saw them,
 * and the first real sync would then skip exactly the rows that were never sent.
 */
internal class FakeDocumentRemoteDataSource : DocumentRemoteDataSource {
    override suspend fun fetchSince(ownerId: String, since: Instant?): List<DocumentDto> = emptyList()
    override suspend fun push(documents: List<DocumentDto>): List<DocumentDto> = documents
}
