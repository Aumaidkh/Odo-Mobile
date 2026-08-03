package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.car.CarDto
import com.hopcape.odo.core.data.car.CarRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlin.time.Instant

/**
 * `cars` over PostgREST.
 *
 * [CarDto] matches the table column for column (DB_SCHEMA §9.3), so it goes over the wire
 * as-is. Soft-deleted rows are pulled too: a tombstone is the only way this device learns a
 * car was removed on another one.
 */
internal class SupabaseCarRemoteDataSource(
    private val postgrest: PostgrestClient,
) : CarRemoteDataSource {

    override suspend fun fetchSince(ownerId: String, since: Instant?): List<CarDto> =
        postgrest.select(
            table = TABLE,
            serializer = CarDto.serializer(),
            filters = buildMap {
                put(COLUMN_OWNER_ID, "eq.$ownerId")
                since?.let { put(COLUMN_UPDATED_AT, "gt.$it") }
            },
            order = "$COLUMN_UPDATED_AT.asc",
        )

    override suspend fun push(cars: List<CarDto>): List<CarDto> =
        postgrest.upsert(table = TABLE, serializer = CarDto.serializer(), rows = cars)

    private companion object {
        const val TABLE = "cars"
        const val COLUMN_OWNER_ID = "owner_id"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}
