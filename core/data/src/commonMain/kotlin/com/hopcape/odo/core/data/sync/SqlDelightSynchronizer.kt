package com.hopcape.odo.core.data.sync

import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.sync.SyncCursor
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Synchronizer
import kotlin.time.Instant

/**
 * The engine's bookkeeping, backed by the same database the rows live in.
 *
 * This is the [Synchronizer] side of the seam: a `Syncable` asks it where it got to, and
 * tells it where it got to now. It lives in `:core:data` rather than `:core:sync` for the
 * obvious reason — it is the only half that needs SQLDelight, and `:core:sync` is kept free
 * of a database on purpose (SYNC_DESIGN §5.1).
 *
 * **Reads are lenient, writes are exact.** An entity that has never synced has no row, and
 * [cursor] answers with an empty [SyncCursor] rather than creating one — a first run is not
 * an error, and a read should not write. [updateCursor] creates the row when it needs to.
 *
 * Failures here are swallowed into telemetry rather than thrown. A cursor that cannot be
 * read is a full re-pull, and a cursor that cannot be saved is a re-pull next run; both are
 * correct, because applying a pulled row is idempotent. Throwing instead would fail a sync
 * run over its own bookkeeping.
 */
internal class SqlDelightSynchronizer(
    private val database: OdoDatabase,
    private val telemetry: DataTelemetry,
) : Synchronizer {

    private val queries get() = database.syncStateQueries

    override suspend fun cursor(entity: SyncEntity): SyncCursor =
        telemetry.span(DataTelemetry.SYNC, OP_CURSOR, entity.name) {
            try {
                queries.selectByEntity(entity.name).executeAsOneOrNull()?.let { row ->
                    SyncCursor(
                        entity = entity,
                        lastPulledAt = row.last_pulled_at?.toInstantOrNull(),
                        lastPushedAt = row.last_pushed_at?.toInstantOrNull(),
                        lastError = row.last_error,
                    )
                } ?: SyncCursor(entity)
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.SYNC, OP_CURSOR, e, entity.name)
                // An unreadable cursor means "pull everything", which costs bandwidth and
                // loses nothing. Refusing to sync would lose more.
                SyncCursor(entity)
            }
        }

    override suspend fun updateCursor(entity: SyncEntity, update: SyncCursor.() -> SyncCursor) {
        telemetry.span(DataTelemetry.SYNC, OP_UPDATE_CURSOR, entity.name) {
            try {
                // Read-modify-write inside one transaction: two entities never share a row,
                // but a run and a retry of the same entity could otherwise interleave.
                database.transaction {
                    val current = queries.selectByEntity(entity.name).executeAsOneOrNull()?.let { row ->
                        SyncCursor(
                            entity = entity,
                            lastPulledAt = row.last_pulled_at?.toInstantOrNull(),
                            lastPushedAt = row.last_pushed_at?.toInstantOrNull(),
                            lastError = row.last_error,
                        )
                    } ?: SyncCursor(entity)

                    val next = current.update()
                    queries.insertIgnore(entity.name)
                    queries.update(
                        lastPulledAt = next.lastPulledAt?.toString(),
                        lastPushedAt = next.lastPushedAt?.toString(),
                        lastError = next.lastError,
                        entity = entity.name,
                    )
                }
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.SYNC, OP_UPDATE_CURSOR, e, entity.name)
            }
        }
    }

    override suspend fun recordFailure(entity: SyncEntity, cause: Throwable) {
        // The type name, never the message: a Ktor or PostgREST message can quote the
        // request, and these rows are the owner's records.
        val reason = cause::class.simpleName ?: UNKNOWN_FAILURE
        telemetry.failed(DataTelemetry.SYNC, OP_RECORD_FAILURE, cause, entity.name)
        updateCursor(entity) { copy(lastError = reason) }
    }

    /**
     * A stored timestamp, or null if it cannot be parsed.
     *
     * Tolerant on purpose: a malformed cursor should degrade to a full re-pull, not crash a
     * sync run. The only way one gets here is a hand-edited database or a format change.
     */
    private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

    private companion object {
        const val OP_CURSOR = "cursor"
        const val OP_UPDATE_CURSOR = "updateCursor"
        const val OP_RECORD_FAILURE = "recordFailure"
        const val UNKNOWN_FAILURE = "UnknownFailure"
    }
}
