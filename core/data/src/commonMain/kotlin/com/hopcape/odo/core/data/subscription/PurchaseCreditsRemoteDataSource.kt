package com.hopcape.odo.core.data.subscription

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The server side of what the owner bought one at a time.
 *
 * Why it syncs at all: the store reports a completed purchase forever, so a record of what
 * has been honoured that only lives on the device lets a reinstall honour the same purchase
 * again. Server-side, it is honoured once for the owner.
 *
 * The server stores; it does not decide. A claim is written by the device that read the
 * purchase from the store, the same way an answer or a service log is. That keeps a purchase
 * made offline crediting immediately, which a webhook-only path could not.
 */
interface PurchaseClaimRemoteDataSource {

    /** The owner's claims changed since [since] (null = never synced). */
    suspend fun fetchSince(ownerId: String, since: Instant?): List<PurchaseClaimDto>

    /** Send local changes; the returned rows are the server's accepted versions. */
    suspend fun push(claims: List<PurchaseClaimDto>): List<PurchaseClaimDto>
}

/** The server side of what the owner has spent of it. */
interface CreditSpendRemoteDataSource {

    suspend fun fetchSince(ownerId: String, since: Instant?): List<CreditSpendDto>

    suspend fun push(spends: List<CreditSpendDto>): List<CreditSpendDto>
}

/**
 * The wire shape of a claim, snake_case to match the Postgres columns.
 *
 * Every column is listed, nullable ones included. A field left out of a PostgREST batch never
 * syncs, and rows that disagree about which columns they carry are rejected as PGRST102.
 */
@Serializable
data class PurchaseClaimDto(
    @SerialName("id") val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("transaction_id") val transactionId: String,
    /** What it was worth when it was honoured, not what the product grants today. */
    @SerialName("scan_checks") val scanChecks: Int,
    @SerialName("record_exports") val recordExports: Int,
    @SerialName("claimed_at") val claimedAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class CreditSpendDto(
    @SerialName("id") val id: String,
    @SerialName("owner_id") val ownerId: String,
    /** `BILL_CHECK` or `RECORD_EXPORT`. */
    @SerialName("kind") val kind: String,
    @SerialName("spent_at") val spentAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/** Accepts pushes, remembers nothing, has nothing to pull. */
internal class FakePurchaseClaimRemoteDataSource : PurchaseClaimRemoteDataSource {
    override suspend fun fetchSince(ownerId: String, since: Instant?): List<PurchaseClaimDto> = emptyList()
    override suspend fun push(claims: List<PurchaseClaimDto>): List<PurchaseClaimDto> = claims
}

/** Accepts pushes, remembers nothing, has nothing to pull. */
internal class FakeCreditSpendRemoteDataSource : CreditSpendRemoteDataSource {
    override suspend fun fetchSince(ownerId: String, since: Instant?): List<CreditSpendDto> = emptyList()
    override suspend fun push(spends: List<CreditSpendDto>): List<CreditSpendDto> = spends
}
