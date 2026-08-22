package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.health.HealthScoreDto
import com.hopcape.odo.core.data.health.HealthScoreRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlin.time.Instant

/**
 * `health_scores` over PostgREST.
 *
 * Ordered by `computed_at`, not `updated_at`, on the pull — the table is append-only, so the
 * two move together, and `computed_at` is the one the local index is built on.
 */
internal class SupabaseHealthScoreRemoteDataSource(
    private val postgrest: PostgrestClient,
) : HealthScoreRemoteDataSource {

    override suspend fun fetchSince(ownerId: String, since: Instant?): List<HealthScoreDto> =
        postgrest.select(
            table = TABLE,
            serializer = HealthScoreDto.serializer(),
            filters = buildMap {
                put(COLUMN_OWNER_ID, "eq.$ownerId")
                since?.let { put(COLUMN_UPDATED_AT, "gt.$it") }
            },
            order = "$COLUMN_UPDATED_AT.asc",
        )

    override suspend fun push(snapshots: List<HealthScoreDto>): List<HealthScoreDto> =
        postgrest.upsert(table = TABLE, serializer = HealthScoreDto.serializer(), rows = snapshots)

    private companion object {
        const val TABLE = "health_scores"
        const val COLUMN_OWNER_ID = "owner_id"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}
