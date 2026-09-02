package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.city.CityDto
import com.hopcape.odo.core.data.city.CityRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlin.time.Instant

/**
 * `cities` over PostgREST — a plain, unscoped select filtered by `updated_at`.
 *
 * This table carries no `owner_id` and no RLS beyond "anyone active may read"
 * (SUPABASE_BOOTSTRAP §2 `read_cities`), like
 * [com.hopcape.odo.infrastructure.supabase.adapters.SupabaseVehicleCatalogRemoteDataSource]'s
 * makes/models fetch — except this one runs as a delta pull through the ordinary sync engine
 * rather than a full fetch-and-replace, so [since] is an actual filter rather than unused.
 */
internal class SupabaseCityRemoteDataSource(
    private val postgrest: PostgrestClient,
) : CityRemoteDataSource {

    override suspend fun fetchSince(since: Instant?): List<CityDto> =
        postgrest.select(
            table = TABLE,
            serializer = CityDto.serializer(),
            filters = since?.let { mapOf(COLUMN_UPDATED_AT to "gte.$it") }.orEmpty(),
            order = "$COLUMN_UPDATED_AT.asc",
        )

    private companion object {
        const val TABLE = "cities"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}
