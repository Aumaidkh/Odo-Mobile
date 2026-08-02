package com.hopcape.odo.core.data.servicelog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The server side of the service log, as the repository needs it: push what changed
 * locally, pull what changed remotely.
 *
 * A port, declared here in `:core:data` because this is the layer that knows how a row
 * becomes a payload. `:core:network` does not exist yet; when it does, it implements this
 * and the fake below is deleted — the repository does not change either way. That is the
 * whole point of introducing it now rather than letting the repository reach for a Supabase
 * client directly and having to be unpicked later.
 *
 * Both halves are shaped for the sync engine (SYNC_DESIGN §6): [fetchSince] is the delta
 * pull keyed on a cursor, [push] is the outbox drain that returns what the server stored so
 * the local rows can take their `remote_version` and go SYNCED.
 */
interface ServiceLogRemoteDataSource {

    /** Entries changed since [since] (null = never synced, so everything). */
    suspend fun fetchSince(carId: String, since: Instant?): List<ServiceLogDto>

    /** Send local changes; the returned rows are the server's accepted versions. */
    suspend fun push(entries: List<ServiceLogDto>): List<ServiceLogDto>
}

/**
 * The wire shape of a service log — snake_case to match the Postgres columns, so the same
 * DTO serves the Supabase REST payload without a second mapping.
 *
 * `categories` rides inside the parent rather than as its own resource: they are a
 * projection of the entry with no identity of their own, which is why the junction table
 * has no sync columns and no `SyncEntity` slot.
 */
@Serializable
data class ServiceLogDto(
    @SerialName("id") val id: String,
    @SerialName("car_id") val carId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("service_date") val serviceDate: String,
    @SerialName("odometer_km") val odometerKm: Int,
    @SerialName("total_amount_paise") val totalAmountPaise: Long,
    @SerialName("workshop_name") val workshopName: String? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("source") val source: String,
    @SerialName("bill_id") val billId: String? = null,
    @SerialName("bill_photo_path") val billPhotoPath: String? = null,
    @SerialName("fairness_snapshot") val fairnessSnapshot: String? = null,
    @SerialName("categories") val categories: List<String> = emptyList(),
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
internal class FakeServiceLogRemoteDataSource : ServiceLogRemoteDataSource {
    override suspend fun fetchSince(carId: String, since: Instant?): List<ServiceLogDto> = emptyList()
    override suspend fun push(entries: List<ServiceLogDto>): List<ServiceLogDto> = entries
}
