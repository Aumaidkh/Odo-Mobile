package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.subscription.CreditSpendDto
import com.hopcape.odo.core.data.subscription.CreditSpendRemoteDataSource
import com.hopcape.odo.core.data.subscription.PurchaseClaimDto
import com.hopcape.odo.core.data.subscription.PurchaseClaimRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlin.time.Instant

/**
 * `purchase_claims` over PostgREST.
 *
 * Resolved on `(owner_id, transaction_id)` rather than `id`: the same purchase honoured
 * offline on two devices is two ids for one transaction, and resolving on `id` would make the
 * second an INSERT that violates the pair's unique index. A 409 is permanent — the row parks
 * in `CONFLICT` and never syncs again — and here that would mean the same purchase credited
 * twice, which is the whole thing this table exists to stop.
 */
internal class SupabasePurchaseClaimRemoteDataSource(
    private val postgrest: PostgrestClient,
) : PurchaseClaimRemoteDataSource {

    override suspend fun fetchSince(ownerId: String, since: Instant?): List<PurchaseClaimDto> =
        postgrest.select(
            table = TABLE,
            serializer = PurchaseClaimDto.serializer(),
            filters = buildMap {
                put(COLUMN_OWNER_ID, "eq.$ownerId")
                since?.let { put(COLUMN_UPDATED_AT, "gt.$it") }
            },
            order = "$COLUMN_UPDATED_AT.asc",
        )

    override suspend fun push(claims: List<PurchaseClaimDto>): List<PurchaseClaimDto> =
        postgrest.upsert(
            table = TABLE,
            serializer = PurchaseClaimDto.serializer(),
            rows = claims,
            onConflict = CONFLICT_TARGET,
        )

    private companion object {
        const val TABLE = "purchase_claims"
        const val COLUMN_OWNER_ID = "owner_id"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val CONFLICT_TARGET = "owner_id,transaction_id"
    }
}

/**
 * `credit_spends` over PostgREST.
 *
 * Resolved on `id`, unlike the claims beside it: a spend has no natural key. Two devices
 * spending offline are two different events and both count.
 */
internal class SupabaseCreditSpendRemoteDataSource(
    private val postgrest: PostgrestClient,
) : CreditSpendRemoteDataSource {

    override suspend fun fetchSince(ownerId: String, since: Instant?): List<CreditSpendDto> =
        postgrest.select(
            table = TABLE,
            serializer = CreditSpendDto.serializer(),
            filters = buildMap {
                put(COLUMN_OWNER_ID, "eq.$ownerId")
                since?.let { put(COLUMN_UPDATED_AT, "gt.$it") }
            },
            order = "$COLUMN_UPDATED_AT.asc",
        )

    override suspend fun push(spends: List<CreditSpendDto>): List<CreditSpendDto> =
        postgrest.upsert(
            table = TABLE,
            serializer = CreditSpendDto.serializer(),
            rows = spends,
            onConflict = COLUMN_ID,
        )

    private companion object {
        const val TABLE = "credit_spends"
        const val COLUMN_ID = "id"
        const val COLUMN_OWNER_ID = "owner_id"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}
