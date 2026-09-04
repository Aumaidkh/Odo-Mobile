package com.hopcape.odo.infrastructure.database.scan

import com.hopcape.odo.core.data.scan.BillCheckLedgerLocalDataSource
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlin.time.Clock

/**
 * SQLDelight-backed [BillCheckLedgerLocalDataSource].
 *
 * The insert decides the winner: `INSERT OR IGNORE` then `changes()` in one transaction, so
 * two reads of the same bill racing each other cannot both be told they were the first.
 */
internal class SqlDelightBillCheckLedgerLocalDataSource(
    private val database: OdoDatabase,
    private val clock: Clock,
) : BillCheckLedgerLocalDataSource {

    override suspend fun claim(billId: String): Boolean =
        database.transactionWithResult {
            database.checkedBillQueries.claimBill(
                serviceLogId = billId,
                checkedAt = clock.now().toString(),
            )
            database.checkedBillQueries.claimedRows().executeAsOne() == 1L
        }

    override suspend fun wasChecked(billId: String): Boolean =
        database.checkedBillQueries.wasChecked(billId).executeAsOne()
}
