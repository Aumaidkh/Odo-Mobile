package com.hopcape.odo.core.data.sync

import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.core.sync.observability.SyncTelemetry
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Push, then pull, for one table — the algorithm every `Syncable` in the app shares.
 *
 * **Push first.** Pulling first would let the last-write-wins comparison see a stale server
 * row as "newer" than a local edit that has not gone up yet, and quietly overwrite the
 * owner's change with their own older data (SYNC_DESIGN §6).
 *
 * Both halves are idempotent, which is what makes a killed run safe. A push is an upsert on
 * a client-generated id, so a retry after a lost response updates the same row rather than
 * creating a twin. A pull re-applies rows it has already applied without harm, which is why
 * the cursor can afford to be conservative.
 *
 * Design: [docs/SYNC_DESIGN.md] §6, §7.
 */
internal class SyncRunner<Dto : Any>(
    private val entity: SyncEntity,
    private val table: SyncTable<Dto>,
    private val database: OdoDatabase,
    private val telemetry: SyncTelemetry,
    private val clock: Clock = Clock.System,
) {

    /**
     * Run both halves.
     *
     * Returns `false` to tell the engine to stop the run and retry later; it never throws,
     * because an exception escaping one table would lose the outcome of the tables that
     * already succeeded. Cancellation is the exception — a run the app called off is not a
     * failure, and swallowing it would leave work running after its scope ended.
     */
    suspend fun run(synchronizer: Synchronizer): Boolean =
        try {
            val pushed = push()
            val pulled = pull(synchronizer)

            synchronizer.updateCursor(entity) {
                copy(
                    lastPushedAt = clock.now(),
                    lastPulledAt = pulled.highWater ?: lastPulledAt,
                    // A run that got here succeeded, so yesterday's failure stops being
                    // shown on the debug row.
                    lastError = null,
                )
            }
            telemetry.moved(entity, pushed = pushed, pulled = pulled.count)
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            synchronizer.recordFailure(entity, e)
            // The rows stay PENDING, so nothing is lost — the next run picks up exactly
            // where this one stopped.
            false
        }

    /**
     * Drain the outbox, and record what the server accepted.
     *
     * The bookkeeping lands in one transaction. A crash between the server storing rows and
     * this device recording that leaves them `PENDING`, which pushes them again next run —
     * and the upsert makes that a no-op rather than a duplicate.
     */
    private suspend fun push(): Int {
        val pending = table.pending()
        if (pending.isEmpty()) return 0

        // Bytes first, always. A row that names an object the server does not have is
        // worse than a row that has not synced yet.
        val stored = table.push(table.uploadBlobs(pending))
        if (stored.isEmpty()) return 0

        var accepted = 0
        database.transaction {
            stored.forEach { dto ->
                val version = table.updatedAtOf(dto)?.toString()
                // No `updated_at` back means the server did not really store it — an
                // offline fake, or a response shape we do not understand. Leaving the row
                // PENDING is the honest answer; marking it SYNCED would make every later
                // run skip exactly the rows that never arrived.
                if (version != null) {
                    table.markSynced(table.idOf(dto), version)
                    accepted++
                }
            }
        }
        return accepted
    }

    /** How far a pull got: how many rows it saw, and the newest server timestamp among them. */
    private data class Pulled(val count: Int, val highWater: Instant?)

    /**
     * Apply the server's changes since the cursor, resolving conflicts by last-write-wins.
     *
     * The rows and the cursor move in the same transaction. A pull killed halfway leaves a
     * cursor that matches the rows actually written, so the next run simply re-fetches the
     * rest.
     */
    private suspend fun pull(synchronizer: Synchronizer): Pulled {
        val cursor = synchronizer.cursor(entity).lastPulledAt
        // `gte` with an overlap rather than `gt`. Two rows can share an `updated_at`, and
        // clock skew between the app and Postgres is real; re-reading a few rows costs
        // nothing because applying one is idempotent, while missing one is permanent loss.
        val remote = table.fetch(cursor?.minus(OVERLAP))
        if (remote.isEmpty()) return Pulled(count = 0, highWater = null)

        var highWater: Instant? = cursor
        // Collected inside the transaction, reported after it: a SQLDelight transaction body
        // is not a suspend context, and telemetry must never dictate the shape of the thing
        // it observes.
        val resolutions = mutableListOf<Boolean>()

        database.transaction {
            remote.forEach { dto ->
                val decision = decide(dto)
                if (decision.applyRemote) table.applyRemote(dto)
                if (decision.wasConflict) resolutions += !decision.applyRemote

                val remoteAt = table.updatedAtOf(dto)
                val current = highWater
                if (remoteAt != null && (current == null || remoteAt > current)) highWater = remoteAt
            }
        }

        resolutions.forEach { localWon -> telemetry.conflictResolved(entity, localWon) }
        return Pulled(count = remote.size, highWater = highWater)
    }

    /**
     * The conflict matrix, as one decision (SYNC_DESIGN §7).
     *
     * The only interesting case is a row that is `PENDING` locally *and* newer on the
     * server, which on a single device means a reinstall or a second device. Ties go to the
     * device: the owner is holding it, and if the two versions are identical the push that
     * follows is a no-op anyway.
     *
     * Every resolution where a real local edit lost is reported, so a conflict storm shows
     * up as a spike rather than as data quietly going missing.
     */
    private fun decide(dto: Dto): Decision {
        // Never seen it — an insert, not a conflict.
        val local = table.localState(table.idOf(dto)) ?: return Decision(applyRemote = true)
        // No local edit is at risk, so the server's version is simply the newer one.
        if (local.syncStatus != SyncStatus.PENDING) return Decision(applyRemote = true)

        // From here a real local edit is on the line, so whichever way it goes is a conflict
        // that gets reported.
        val localAt = local.updatedAt ?: return Decision(applyRemote = true, wasConflict = true)
        // A remote row with no timestamp cannot be shown to be newer, so the edit stands.
        val remoteAt = table.updatedAtOf(dto) ?: return Decision(applyRemote = false, wasConflict = true)

        return Decision(applyRemote = remoteAt > localAt, wasConflict = true)
    }

    /** Whether the pulled row wins, and whether a local edit was at stake when it was decided. */
    private data class Decision(val applyRemote: Boolean, val wasConflict: Boolean = false)

    private companion object {
        val OVERLAP = 5.seconds
    }
}
