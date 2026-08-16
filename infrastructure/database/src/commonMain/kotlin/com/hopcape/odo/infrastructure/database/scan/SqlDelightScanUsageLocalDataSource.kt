package com.hopcape.odo.infrastructure.database.scan

import com.hopcape.odo.core.data.scan.ScanUsageLocalDataSource
import com.hopcape.odo.infrastructure.database.db.OdoDatabase

/**
 * SQLDelight-backed [ScanUsageLocalDataSource].
 *
 * No sync columns and no push: `scan_usage` is device-local and mirrors no server table.
 *
 * [increment] is insert-then-update in one transaction, which is both the SQLite 3.18
 * workaround for a missing UPSERT and what makes the two statements atomic — without the
 * transaction, two scans finishing together could each insert the month's row and one of the
 * updates would be lost.
 */
internal class SqlDelightScanUsageLocalDataSource(
    private val database: OdoDatabase,
) : ScanUsageLocalDataSource {

    private val queries get() = database.scanUsageQueries

    override suspend fun countAllTime(): Int =
        queries.countAllTime().executeAsOneOrNull()?.total?.toInt() ?: 0

    override suspend fun countFor(month: String): Int =
        queries.countForMonth(month).executeAsOneOrNull()?.toInt() ?: 0

    override suspend fun increment(month: String) {
        database.transaction {
            queries.startMonth(month)
            queries.incrementMonth(month)
        }
    }
}
