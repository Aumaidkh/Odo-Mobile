package com.hopcape.odo.core.data.fairness

import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.servicelog.serviceLogFromRow
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.fairness.model.OverchargeReport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * SQLDelight-backed [OverchargeReportLocalDataSource] — fully offline. The local DB is
 * the source of truth; every write stamps `updated_at` and leaves the row
 * `sync_status = PENDING` for the sync engine.
 *
 * Timestamps are client-stamped here (offline-first; the server reconciles on sync).
 */
internal class SqlDelightOverchargeReportLocalDataSource(
    private val database: OdoDatabase,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : OverchargeReportLocalDataSource {

    override suspend fun insert(id: String, report: OverchargeReport): Boolean = withContext(dispatcher) {
        val now = clock.now().toString()
        database.transactionWithResult {
            val entry = database.serviceLogQueries
                .selectById(report.logId.value, ::serviceLogFromRow)
                .executeAsOneOrNull() ?: return@transactionWithResult false

            database.overchargeReportQueries.insertOverchargeReport(
                id = id,
                serviceLogId = report.logId.value,
                ownerId = entry.ownerId.value,
                reason = report.reason.name,
                category = report.category?.name,
                note = report.note,
                now = now,
                syncStatus = SyncStatus.PENDING.name,
            )
            true
        }
    }
}
