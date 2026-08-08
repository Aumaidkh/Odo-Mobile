package com.hopcape.odo.core.data.trip

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The server side of a trip, as the sync table needs it: push what changed locally, pull
 * what changed remotely for a car.
 *
 * A port, declared here in `:core:data` because this is the layer that knows how a row
 * becomes a payload. `SupabaseTripRemoteDataSource` (`:infrastructure:supabase`) implements
 * it once the build carries credentials; [FakeTripRemoteDataSource] stands in until then.
 *
 * Shaped for the sync engine (SYNC_DESIGN §6): [fetchSince] is the delta pull keyed on a
 * cursor, [push] is the outbox drain that returns what the server stored so the local rows
 * can take their `remote_version` and go SYNCED.
 */
interface TripRemoteDataSource {

    /** Trips changed since [since] (null = never synced, so everything) for [carId]. */
    suspend fun fetchSince(carId: String, since: Instant?): List<TripDto>

    /** Send local changes; the returned rows are the server's accepted versions. */
    suspend fun push(trips: List<TripDto>): List<TripDto>
}

/**
 * The wire shape of a trip — snake_case to match the Postgres columns
 * (`docs/DB_SCHEMA.md` §9.13b).
 *
 * **`start_lat`/`start_lon`/`end_lat`/`end_lon` have no fields here, on purpose**
 * (TRIPTRACKER_PLAN D4): a trip's coordinates are device-local forever, and the server
 * table has no columns for them either. Leaving them out of this type is what makes the
 * rule unbreakable at the call site — there is nothing to accidentally send.
 */
@Serializable
data class TripDto(
    @SerialName("id") val id: String,
    @SerialName("car_id") val carId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String,
    @SerialName("distance_m") val distanceM: Long,
    @SerialName("estimated_m") val estimatedM: Long,
    @SerialName("mode") val mode: String,
    @SerialName("status") val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/**
 * Stand-in until Supabase credentials are configured: accepts pushes, remembers nothing,
 * and has nothing to pull.
 *
 * It returns the pushed rows back unchanged, which is honest about what it is — a server
 * that agrees with everything. It deliberately does **not** fabricate a `remote_version`:
 * inventing one would let local rows flip to SYNCED against a server that never saw them,
 * and the first real sync would then skip exactly the rows that were never sent.
 */
internal class FakeTripRemoteDataSource : TripRemoteDataSource {
    override suspend fun fetchSince(carId: String, since: Instant?): List<TripDto> = emptyList()
    override suspend fun push(trips: List<TripDto>): List<TripDto> = trips
}
