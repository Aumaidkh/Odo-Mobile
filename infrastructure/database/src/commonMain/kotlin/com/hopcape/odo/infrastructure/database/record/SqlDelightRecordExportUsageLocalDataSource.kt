package com.hopcape.odo.infrastructure.database.record

import com.hopcape.odo.core.data.record.RecordExportUsageLocalDataSource
import com.hopcape.odo.infrastructure.database.db.OdoDatabase

/**
 * SQLDelight-backed [RecordExportUsageLocalDataSource].
 *
 * No sync columns and no push: `record_export_usage` is device-local and mirrors no server
 * table.
 *
 * [increment] is insert-then-update in one transaction, which is both the SQLite 3.18
 * workaround for a missing UPSERT and what makes the two statements atomic — without the
 * transaction, two exports finishing together could each insert the month's row and one of
 * the updates would be lost.
 */
internal class SqlDelightRecordExportUsageLocalDataSource(
    private val database: OdoDatabase,
) : RecordExportUsageLocalDataSource {

    private val queries get() = database.recordExportUsageQueries

    /** `SUM` over no rows is NULL, which is a tally of zero rather than a missing answer. */
    override suspend fun countAllTime(): Int =
        queries.countAllTime().executeAsOneOrNull()?.total?.toInt() ?: 0

    override suspend fun increment(month: String) {
        database.transaction {
            queries.startMonth(month)
            queries.incrementMonth(month)
        }
    }
}
