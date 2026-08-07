package com.hopcape.odo.infrastructure.database.sync

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.sync.SyncStatusProvider
import com.hopcape.odo.core.sync.SyncRunObserver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * What the UI is allowed to know about sync, read off the same database sync writes to.
 *
 * [pendingCount] and [lastSyncedAt] are queries rather than counters kept in memory, so they
 * are right after a process death and cannot drift from what is actually in the outbox.
 * [isSyncing] is the exception — a run in flight is not a row, so the engine reports it here.
 *
 * Failures collapse to a benign value rather than propagating. A chip that cannot read the
 * outbox should say nothing, not take down the screen it is drawn on.
 */
internal class SqlDelightSyncStatusProvider(
    private val database: OdoDatabase,
    private val telemetry: DataTelemetry,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SyncStatusProvider, SyncRunObserver {

    private val _isSyncing = MutableStateFlow(false)

    override val isSyncing: Flow<Boolean> = _isSyncing.asStateFlow()

    override val pendingCount: Flow<Int> =
        database.syncStateQueries.countAllPending()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.toInt() ?: 0 }
            .catch { cause ->
                // Zero is the safe thing to render, but it is also what "everything is
                // backed up" looks like — so the failure has to be said out loud somewhere.
                telemetry.crashed(DataTelemetry.SYNC, OP_PENDING_COUNT, cause)
                emit(0)
            }

    override val lastSyncedAt: Flow<Instant?> =
        database.syncStateQueries.selectNewestPull()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.MAX?.toInstantOrNull() }
            .catch { cause ->
                telemetry.crashed(DataTelemetry.SYNC, OP_LAST_SYNCED, cause)
                emit(null)
            }

    override val lastError: Flow<String?> =
        database.syncStateQueries.selectAnyError()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .catch { cause ->
                telemetry.crashed(DataTelemetry.SYNC, OP_LAST_ERROR, cause)
                emit(null)
            }

    /** The engine's bookends. Not part of the port — the UI only ever reads. */
    override fun onRunning(running: Boolean) { _isSyncing.value = running }

    private companion object {
        const val OP_PENDING_COUNT = "pendingCount"
        const val OP_LAST_SYNCED = "lastSyncedAt"
        const val OP_LAST_ERROR = "lastError"
    }
}
