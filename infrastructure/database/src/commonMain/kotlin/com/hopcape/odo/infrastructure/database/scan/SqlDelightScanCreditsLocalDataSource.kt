package com.hopcape.odo.infrastructure.database.scan

import com.hopcape.odo.core.data.scan.ScanCreditsLocalDataSource
import com.hopcape.odo.infrastructure.database.db.OdoDatabase

/**
 * SQLDelight-backed [ScanCreditsLocalDataSource].
 *
 * Device-local and unsynced, matching `scan_usage` beside it.
 *
 * Every write is insert-then-update in one transaction: the SQLite 3.18 workaround for a
 * missing UPSERT, and what keeps the two statements atomic. [spend] reads inside the same
 * transaction as its guarded update, so two scans finishing together cannot both take the
 * last credit.
 */
internal class SqlDelightScanCreditsLocalDataSource(
    private val database: OdoDatabase,
) : ScanCreditsLocalDataSource {

    private val queries get() = database.scanUsageQueries

    override suspend fun remaining(): Int =
        queries.scanCreditsRemaining().executeAsOneOrNull()?.toInt() ?: 0

    override suspend fun grant(count: Int) {
        if (count <= 0) return
        database.transaction {
            queries.startScanCredits()
            queries.grantScanCredits(count.toLong())
        }
    }

    override suspend fun spend(): Boolean = database.transactionWithResult {
        queries.startScanCredits()
        val had = (queries.scanCreditsRemaining().executeAsOneOrNull() ?: 0L) > 0L
        if (had) queries.spendScanCredit()
        had
    }
}
