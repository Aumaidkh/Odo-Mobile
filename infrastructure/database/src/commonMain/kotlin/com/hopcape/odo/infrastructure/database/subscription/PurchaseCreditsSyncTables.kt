package com.hopcape.odo.infrastructure.database.subscription

import com.hopcape.odo.core.data.subscription.CreditSpendDto
import com.hopcape.odo.core.data.subscription.CreditSpendRemoteDataSource
import com.hopcape.odo.core.data.subscription.PurchaseClaimDto
import com.hopcape.odo.core.data.subscription.PurchaseClaimRemoteDataSource
import com.hopcape.odo.infrastructure.database.db.Credit_spends
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.db.Purchase_claims
import com.hopcape.odo.infrastructure.database.sync.FetchResult
import com.hopcape.odo.infrastructure.database.sync.LocalRowState
import com.hopcape.odo.infrastructure.database.sync.SyncTable
import com.hopcape.odo.infrastructure.database.sync.orNullIfPlaceholder
import com.hopcape.odo.infrastructure.database.sync.toInstantOrNull
import com.hopcape.odo.infrastructure.database.sync.toSyncStatus
import kotlin.time.Instant

/**
 * `purchase_claims` as the sync algorithm sees it.
 *
 * Rows only, so [uploadBlobs] and `reconcileBeforePush` stay at their no-op defaults. Scoped
 * to the owner, not a car: a purchase belongs to the person who paid for it.
 */
internal class PurchaseClaimSyncTable(
    private val database: OdoDatabase,
    private val remote: PurchaseClaimRemoteDataSource,
    private val ownerId: () -> String?,
) : SyncTable<PurchaseClaimDto> {

    private val queries get() = database.purchaseCreditsQueries

    override fun idOf(dto: PurchaseClaimDto): String = dto.id

    override fun updatedAtOf(dto: PurchaseClaimDto): Instant? = dto.updatedAt.toInstantOrNull()

    override suspend fun pending(): List<PurchaseClaimDto> =
        queries.selectPendingClaims().executeAsList().map(Purchase_claims::toDto)

    override suspend fun push(rows: List<PurchaseClaimDto>): List<PurchaseClaimDto> =
        remote.push(rows)

    override fun markSynced(id: String, remoteVersion: String) =
        queries.markClaimSynced(remoteVersion = remoteVersion, id = id)

    override fun markConflict(id: String) = queries.markClaimConflict(id)

    override suspend fun fetch(since: Instant?): FetchResult<PurchaseClaimDto> {
        val owner = ownerId().orNullIfPlaceholder() ?: return FetchResult.ScopeMissing(OWNER)
        return FetchResult.Rows(remote.fetchSince(owner, since))
    }

    override fun localState(id: String): LocalRowState? =
        queries.selectClaimSyncState(id).executeAsOneOrNull()?.let { row ->
            LocalRowState(
                syncStatus = row.sync_status.toSyncStatus(),
                updatedAt = row.updated_at.toInstantOrNull(),
            )
        }

    /**
     * The insert can be ignored on the unique pair, not only the primary key: the same
     * purchase honoured offline on two devices is two ids for one transaction. The update
     * then matches nothing and the local row stays `PENDING`, which is the right outcome —
     * both rows say the owner was credited once, and a second insert would credit them twice.
     */
    override fun applyRemote(dto: PurchaseClaimDto) {
        queries.insertClaimFromRemote(
            id = dto.id,
            owner_id = dto.ownerId,
            transaction_id = dto.transactionId,
            scan_checks = dto.scanChecks.toLong(),
            record_exports = dto.recordExports.toLong(),
            claimed_at = dto.claimedAt,
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
        )
        queries.updateClaimFromRemote(
            owner_id = dto.ownerId,
            transaction_id = dto.transactionId,
            scan_checks = dto.scanChecks.toLong(),
            record_exports = dto.recordExports.toLong(),
            claimed_at = dto.claimedAt,
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
            id = dto.id,
        )
    }

    private companion object {
        /** Names the missing scope in a log. Never the value. */
        const val OWNER = "owner id"
    }
}

/** `credit_spends` as the sync algorithm sees it. Same shape, no natural key. */
internal class CreditSpendSyncTable(
    private val database: OdoDatabase,
    private val remote: CreditSpendRemoteDataSource,
    private val ownerId: () -> String?,
) : SyncTable<CreditSpendDto> {

    private val queries get() = database.purchaseCreditsQueries

    override fun idOf(dto: CreditSpendDto): String = dto.id

    override fun updatedAtOf(dto: CreditSpendDto): Instant? = dto.updatedAt.toInstantOrNull()

    override suspend fun pending(): List<CreditSpendDto> =
        queries.selectPendingSpends().executeAsList().map(Credit_spends::toDto)

    override suspend fun push(rows: List<CreditSpendDto>): List<CreditSpendDto> = remote.push(rows)

    override fun markSynced(id: String, remoteVersion: String) =
        queries.markSpendSynced(remoteVersion = remoteVersion, id = id)

    override fun markConflict(id: String) = queries.markSpendConflict(id)

    override suspend fun fetch(since: Instant?): FetchResult<CreditSpendDto> {
        val owner = ownerId().orNullIfPlaceholder() ?: return FetchResult.ScopeMissing(OWNER)
        return FetchResult.Rows(remote.fetchSince(owner, since))
    }

    override fun localState(id: String): LocalRowState? =
        queries.selectSpendSyncState(id).executeAsOneOrNull()?.let { row ->
            LocalRowState(
                syncStatus = row.sync_status.toSyncStatus(),
                updatedAt = row.updated_at.toInstantOrNull(),
            )
        }

    override fun applyRemote(dto: CreditSpendDto) {
        queries.insertSpendFromRemote(
            id = dto.id,
            owner_id = dto.ownerId,
            kind = dto.kind,
            spent_at = dto.spentAt,
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
        )
        queries.updateSpendFromRemote(
            owner_id = dto.ownerId,
            kind = dto.kind,
            spent_at = dto.spentAt,
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
            id = dto.id,
        )
    }

    private companion object {
        const val OWNER = "owner id"
    }
}

private fun Purchase_claims.toDto() = PurchaseClaimDto(
    id = id,
    ownerId = owner_id,
    transactionId = transaction_id,
    scanChecks = scan_checks.toInt(),
    recordExports = record_exports.toInt(),
    claimedAt = claimed_at,
    createdAt = created_at,
    updatedAt = updated_at,
    deletedAt = deleted_at,
)

private fun Credit_spends.toDto() = CreditSpendDto(
    id = id,
    ownerId = owner_id,
    kind = kind,
    spentAt = spent_at,
    createdAt = created_at,
    updatedAt = updated_at,
    deletedAt = deleted_at,
)
