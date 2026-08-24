package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.trip.TripDto
import com.hopcape.odo.core.data.trip.TripRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlin.time.Instant

/**
 * `trips` over PostgREST (`docs/DB_SCHEMA.md` §9.13b).
 *
 * The delta pull is `owner_id = ? AND updated_at > cursor`, ordered by `updated_at` so the
 * cursor advances monotonically. Soft-deleted rows are **not** filtered out: a tombstone is
 * the only way the device learns a trip was deleted elsewhere (SYNC_DESIGN §6).
 *
 * [TripDto] has no coordinate fields at all (TRIPTRACKER_PLAN D4), so unlike
 * `SupabaseServiceLogRemoteDataSource` there is no second wire type to strip anything from
 * — the DTO already matches the table column for column.
 */
internal class SupabaseTripRemoteDataSource(
    private val postgrest: PostgrestClient,
) : TripRemoteDataSource {

    override suspend fun fetchSince(ownerId: String, since: Instant?): List<TripDto> =
        postgrest.select(
            table = TABLE,
            serializer = TripDto.serializer(),
            filters = buildMap {
                put(COLUMN_OWNER_ID, "eq.$ownerId")
                since?.let { put(COLUMN_UPDATED_AT, "gt.$it") }
            },
            order = "$COLUMN_UPDATED_AT.asc",
        )

    override suspend fun push(trips: List<TripDto>): List<TripDto> =
        postgrest.upsert(table = TABLE, serializer = TripDto.serializer(), rows = trips)

    private companion object {
        const val TABLE = "trips"
        const val COLUMN_OWNER_ID = "owner_id"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}
