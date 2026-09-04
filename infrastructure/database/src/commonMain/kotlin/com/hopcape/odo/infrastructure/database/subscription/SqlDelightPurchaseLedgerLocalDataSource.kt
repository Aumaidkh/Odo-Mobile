package com.hopcape.odo.infrastructure.database.subscription

import com.hopcape.odo.core.data.subscription.PurchaseLedgerLocalDataSource
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlin.time.Clock

/**
 * SQLDelight-backed [PurchaseLedgerLocalDataSource].
 *
 * The insert decides the winner: `INSERT OR IGNORE` followed by `changes()` inside one
 * transaction, so two reconciles racing each other cannot both be told they claimed the
 * same purchase. Reading first and inserting after would leave exactly that window open, and
 * the thing on the other side of it is a duplicated grant.
 */
internal class SqlDelightPurchaseLedgerLocalDataSource(
    private val database: OdoDatabase,
    private val clock: Clock,
) : PurchaseLedgerLocalDataSource {

    override suspend fun claim(transactionId: String): Boolean =
        database.transactionWithResult {
            database.claimedPurchaseQueries.claimPurchase(
                transactionId = transactionId,
                claimedAt = clock.now().toString(),
            )
            database.claimedPurchaseQueries.claimedRows().executeAsOne() == 1L
        }
}
