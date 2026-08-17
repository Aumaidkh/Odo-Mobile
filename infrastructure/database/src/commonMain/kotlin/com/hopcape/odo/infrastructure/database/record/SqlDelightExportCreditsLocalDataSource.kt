package com.hopcape.odo.infrastructure.database.record

import com.hopcape.odo.core.data.record.ExportCreditsLocalDataSource
import com.hopcape.odo.infrastructure.database.db.OdoDatabase

/**
 * SQLDelight-backed [ExportCreditsLocalDataSource].
 *
 * Device-local and unsynced, matching `record_export_usage` beside it.
 *
 * Every write is insert-then-update in one transaction: the SQLite 3.18 workaround for a
 * missing UPSERT, and what keeps the two statements atomic. [spend] reads inside the same
 * transaction as its guarded update, so two shares finishing together cannot both take the
 * last credit.
 */
internal class SqlDelightExportCreditsLocalDataSource(
    private val database: OdoDatabase,
) : ExportCreditsLocalDataSource {

    private val queries get() = database.recordExportUsageQueries

    override suspend fun remaining(): Int =
        queries.creditsRemaining().executeAsOneOrNull()?.toInt() ?: 0

    override suspend fun grant() {
        database.transaction {
            queries.startCredits()
            queries.grantCredit()
        }
    }

    override suspend fun spend(): Boolean = database.transactionWithResult {
        queries.startCredits()
        val had = (queries.creditsRemaining().executeAsOneOrNull() ?: 0L) > 0L
        if (had) queries.spendCredit()
        had
    }
}
