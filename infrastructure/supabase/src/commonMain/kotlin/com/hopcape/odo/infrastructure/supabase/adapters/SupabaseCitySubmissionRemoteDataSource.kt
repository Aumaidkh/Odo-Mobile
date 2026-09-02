package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.city.CitySubmissionDto
import com.hopcape.odo.core.data.city.CitySubmissionRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient

/**
 * `city_submissions` over PostgREST.
 *
 * [push] is deliberately a plain [PostgrestClient.insert] with `returnRows = false`, never
 * [PostgrestClient.upsert] and never `return=representation` — the same reasoning as
 * [com.hopcape.odo.infrastructure.supabase.adapters.SupabaseVehicleCatalogRemoteDataSource.push]:
 * `city_submissions` grants `authenticated`
 * INSERT only, no UPDATE, no SELECT, so an upsert's `ON CONFLICT DO UPDATE` plan and a plain
 * `RETURNING` both fail RLS with `42501` for a table with neither policy.
 *
 * [push] answers with [submissions] itself rather than a server-echoed list, for the same
 * reason — the accepted rows can never be read back to confirm what the server stored.
 */
internal class SupabaseCitySubmissionRemoteDataSource(
    private val postgrest: PostgrestClient,
) : CitySubmissionRemoteDataSource {

    override suspend fun push(submissions: List<CitySubmissionDto>): List<CitySubmissionDto> {
        postgrest.insert(
            table = TABLE,
            serializer = CitySubmissionDto.serializer(),
            rows = submissions,
            returnRows = false,
        )
        return submissions
    }

    private companion object {
        const val TABLE = "city_submissions"
    }
}
