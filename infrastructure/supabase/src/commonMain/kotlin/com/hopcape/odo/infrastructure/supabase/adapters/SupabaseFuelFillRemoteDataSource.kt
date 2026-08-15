package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.cost.FuelFillDto
import com.hopcape.odo.core.data.cost.FuelFillRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlin.time.Instant

/**
 * `fuel_fills` over PostgREST.
 *
 * The delta pull is `car_id = ? AND updated_at > cursor`, ordered by `updated_at` so the
 * cursor advances monotonically. Soft-deleted rows are **not** filtered out: a tombstone is
 * the only way this device learns a fill was deleted elsewhere (SYNC_DESIGN §6).
 *
 * [FuelFillDto] matches the table column for column, so unlike
 * [SupabaseServiceLogRemoteDataSource] there is no second wire type to strip anything from.
 */
internal class SupabaseFuelFillRemoteDataSource(
    private val postgrest: PostgrestClient,
) : FuelFillRemoteDataSource {

    override suspend fun fetchSince(carId: String, since: Instant?): List<FuelFillDto> =
        postgrest.select(
            table = TABLE,
            serializer = FuelFillDto.serializer(),
            filters = buildMap {
                put(COLUMN_CAR_ID, "eq.$carId")
                since?.let { put(COLUMN_UPDATED_AT, "gt.$it") }
            },
            order = "$COLUMN_UPDATED_AT.asc",
        )

    override suspend fun push(fills: List<FuelFillDto>): List<FuelFillDto> =
        postgrest.upsert(table = TABLE, serializer = FuelFillDto.serializer(), rows = fills)

    private companion object {
        const val TABLE = "fuel_fills"
        const val COLUMN_CAR_ID = "car_id"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}
