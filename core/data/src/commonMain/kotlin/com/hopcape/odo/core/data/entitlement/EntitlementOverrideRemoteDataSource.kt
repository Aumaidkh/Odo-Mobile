package com.hopcape.odo.core.data.entitlement

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The server side of entitlement granted outside the store.
 *
 * Scoped to the owner, unlike the cities lookup: these rows are about one account
 * and RLS only ever returns that account's own. [since] is an ordinary delta
 * cursor.
 */
interface EntitlementOverrideRemoteDataSource {

    /** Every override changed since [since] (null = never synced). */
    suspend fun fetchSince(since: Instant?): List<EntitlementOverrideDto>
}

/** One row of `entitlement_overrides` — snake_case to match the Postgres columns. */
@Serializable
data class EntitlementOverrideDto(
    @SerialName("owner_id") val ownerId: String,
    @SerialName("feature") val feature: String,
    /**
     * False is a deliberate revoke, not an absent row.
     *
     * Support cutting somebody off while the store still says they have paid has
     * to be distinguishable from nobody having looked at the account.
     */
    @SerialName("granted") val granted: Boolean,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("granted_at") val grantedAt: String,
)

/** Stand-in for builds with no Supabase configuration: nobody has an override. */
internal class FakeEntitlementOverrideRemoteDataSource : EntitlementOverrideRemoteDataSource {
    override suspend fun fetchSince(since: Instant?): List<EntitlementOverrideDto> = emptyList()
}
