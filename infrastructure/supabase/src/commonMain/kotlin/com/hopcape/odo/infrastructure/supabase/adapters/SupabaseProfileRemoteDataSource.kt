package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.owner.ProfileDto
import com.hopcape.odo.core.data.owner.ProfileRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlin.time.Instant

/**
 * `profiles` over PostgREST.
 *
 * Filtered on `id`, not `owner_id`: a profile's primary key *is* the owner, because the row
 * is `auth.users.id`. RLS would allow only this row through anyway; the filter is here so a
 * pull is one row rather than a table scan the policy then narrows.
 */
internal class SupabaseProfileRemoteDataSource(
    private val postgrest: PostgrestClient,
) : ProfileRemoteDataSource {

    override suspend fun fetchSince(ownerId: String, since: Instant?): List<ProfileDto> =
        postgrest.select(
            table = TABLE,
            serializer = ProfileDto.serializer(),
            filters = buildMap {
                put(COLUMN_ID, "eq.$ownerId")
                since?.let { put(COLUMN_UPDATED_AT, "gt.$it") }
            },
        )

    override suspend fun push(profiles: List<ProfileDto>): List<ProfileDto> =
        postgrest.upsert(table = TABLE, serializer = ProfileDto.serializer(), rows = profiles)

    private companion object {
        const val TABLE = "profiles"
        const val COLUMN_ID = "id"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}
