package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.entitlement.EntitlementOverrideDto
import com.hopcape.odo.core.data.entitlement.EntitlementOverrideRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlin.time.Instant

/**
 * `entitlement_overrides` over PostgREST — a delta pull on `granted_at`.
 *
 * No owner filter in the query, and that is not an oversight: the table's read
 * policy is `owner_id = auth.uid()`, so the server returns this account's rows and
 * only this account's. Adding a client-side filter would restate a rule that is
 * already enforced where it cannot be bypassed.
 *
 * There is no push half. The client never writes here — an entitlement a device
 * could grant itself would not be an entitlement.
 */
internal class SupabaseEntitlementOverrideRemoteDataSource(
    private val postgrest: PostgrestClient,
) : EntitlementOverrideRemoteDataSource {

    override suspend fun fetchSince(since: Instant?): List<EntitlementOverrideDto> =
        postgrest.select(
            table = TABLE,
            serializer = EntitlementOverrideDto.serializer(),
            filters = since?.let { mapOf(COLUMN_GRANTED_AT to "gte.$it") }.orEmpty(),
            order = "$COLUMN_GRANTED_AT.asc",
        )

    private companion object {
        const val TABLE = "entitlement_overrides"
        const val COLUMN_GRANTED_AT = "granted_at"
    }
}
