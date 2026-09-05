package com.hopcape.odo.infrastructure.database.subscription

import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.data.subscription.PurchaseCreditsLocalDataSource
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.subscription.CreditKind
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlin.time.Clock

/**
 * SQLDelight-backed [PurchaseCreditsLocalDataSource].
 *
 * The balance is not stored. [available] is what the claims granted minus what the spends
 * took, both read in one transaction so a spend landing between the two reads cannot make
 * the answer larger than it is.
 *
 * Every write stamps `updated_at` and leaves the row `PENDING` (SYNC_DESIGN §4). Rows written
 * before sign-in carry the placeholder owner and are re-stamped by adoption, like every other
 * table's.
 */
internal class SqlDelightPurchaseCreditsLocalDataSource(
    private val database: OdoDatabase,
    private val ids: IdGenerator,
    private val owners: CurrentOwnerProvider,
    private val clock: Clock,
) : PurchaseCreditsLocalDataSource {

    private val queries get() = database.purchaseCreditsQueries

    /**
     * `INSERT OR IGNORE` then `changes()`, in one transaction: the insert itself decides who
     * honoured the transaction, so two passes racing cannot both be told they did. Reading
     * first and inserting after would leave exactly that window open, and on the other side
     * of it is a duplicated credit.
     */
    override suspend fun claim(
        transactionId: String,
        scanChecks: Int,
        recordExports: Int,
    ): Boolean {
        val owner = owners.currentOwnerId().value
        val now = clock.now().toString()
        return database.transactionWithResult {
            queries.insertClaim(
                id = ids.newId(),
                ownerId = owner,
                transactionId = transactionId,
                scanChecks = scanChecks.toLong(),
                recordExports = recordExports.toLong(),
                now = now,
            )
            queries.changedRows().executeAsOne() == 1L
        }
    }

    override suspend fun available(kind: CreditKind): Int =
        database.transactionWithResult { balanceOf(kind) }

    /**
     * Guarded so a balance can never go negative: two shares racing each other both run this
     * and only the one that finds a credit writes a row. The read and the insert are in one
     * transaction, which is what makes the guard hold.
     */
    override suspend fun spend(kind: CreditKind): Boolean {
        val owner = owners.currentOwnerId().value
        val now = clock.now().toString()
        return database.transactionWithResult {
            if (balanceOf(kind) <= 0) return@transactionWithResult false
            queries.insertSpend(
                id = ids.newId(),
                ownerId = owner,
                kind = kind.name,
                now = now,
            )
            true
        }
    }

    /** Granted minus spent. Call inside a transaction — both reads have to see one state. */
    private fun balanceOf(kind: CreditKind): Int {
        val granted = when (kind) {
            CreditKind.BILL_CHECK -> queries.scanChecksGranted().executeAsOne()
            CreditKind.RECORD_EXPORT -> queries.recordExportsGranted().executeAsOne()
        }
        val spent = queries.spendCount(kind.name).executeAsOne()
        return (granted - spent).coerceAtLeast(0L).toInt()
    }
}
