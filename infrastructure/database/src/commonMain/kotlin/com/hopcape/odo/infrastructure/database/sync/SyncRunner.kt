package com.hopcape.odo.infrastructure.database.sync

import com.hopcape.odo.core.data.sync.SyncRejection
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.core.sync.observability.SyncTelemetry
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Push and pull for one table — the algorithm every `Syncable` in the app shares.
 *
 * **Two entry points, not one.** [push] and [pull] are called separately because the engine
 * runs them as separate phases: every entity pushes, then every entity pulls. They used to
 * be a single `run`, which meant both halves shared the push's stop-at-first-failure rule
 * and a profile that would not sync stopped cars from ever being fetched (issue #312).
 *
 * **Push still happens first**, for every entity, before any pull. Pulling first would let
 * the last-write-wins comparison see a stale server row as "newer" than a local edit that
 * has not gone up yet, and quietly overwrite the owner's change with their own older data
 * (SYNC_DESIGN §6).
 *
 * Both halves are idempotent, which is what makes a killed run safe. A push is an upsert on
 * a client-generated id, so a retry after a lost response updates the same row rather than
 * creating a twin. A pull re-applies rows it has already applied without harm, which is why
 * the cursor can afford to be conservative.
 *
 * **A permanent refusal does not stop the run.** A row the server has decided against gets
 * the same answer every time it is sent, so leaving it `PENDING` means the next run fails on
 * it too and every table after this one is never reached — one bad row takes the whole app
 * offline. Those rows are isolated and marked `CONFLICT` instead. Everything else — a
 * timeout, a 5xx, a dropped connection — still stops the run and is retried.
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
     * Drain the outbox and move the push half of the cursor.
     *
     * Returns `false` to tell the engine to stop the push phase and retry later; it never
     * throws, because an exception escaping one table would lose the outcome of the tables
     * that already succeeded. Cancellation is the exception — a run the app called off is
     * not a failure, and swallowing it would leave work running after its scope ended.
     */
    suspend fun push(synchronizer: Synchronizer): Boolean =
        attempt(synchronizer) {
            val pushed = drainOutbox()
            synchronizer.updateCursor(entity) {
                copy(
                    lastPushedAt = clock.now(),
                    // A push that got here reconciled, so yesterday's failure stops being
                    // shown on the debug row — unless rows were refused on the way past, in
                    // which case that is the current state of this entity and has to stand.
                    // Safe to clear here rather than after the pull: the push phase runs
                    // first, so a pull that fails later in the same run overwrites this.
                    lastError = pushed.refusal,
                )
            }
            telemetry.moved(entity, phase = PHASE_PUSH, pushed = pushed.accepted)
        }

    /**
     * Apply the server's changes and move the pull half of the cursor.
     *
     * Returns `false` the same way [push] does, but the engine treats it differently: the
     * pull phase carries on to the next entity rather than stopping, because fetching has no
     * ordering requirement.
     */
    suspend fun pull(synchronizer: Synchronizer): Boolean =
        attempt(synchronizer) {
            val pulled = applyRemoteChanges(synchronizer)
            synchronizer.updateCursor(entity) {
                copy(lastPulledAt = pulled.highWater ?: lastPulledAt)
            }
            telemetry.moved(entity, phase = PHASE_PULL, pulled = pulled.count)
        }

    /** The failure handling both halves share. */
    private suspend fun attempt(synchronizer: Synchronizer, half: suspend () -> Unit): Boolean =
        try {
            half()
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            synchronizer.recordFailure(entity, e)
            // The rows stay PENDING, so nothing is lost — the next run picks up exactly
            // where this one stopped.
            false
        }

    /** What a push achieved: rows the server took, and a refusal worth showing if it made one. */
    private data class Pushed(val accepted: Int, val refusal: String? = null)

    /**
     * Drain the outbox, and record what the server accepted.
     *
     * The bookkeeping lands in one transaction. A crash between the server storing rows and
     * this device recording that leaves them `PENDING`, which pushes them again next run —
     * and the upsert makes that a no-op rather than a duplicate.
     *
     * A **permanent** refusal is not a reason to stop. The server has decided about the
     * payload, so the same batch gets the same answer forever, and leaving it in the outbox
     * means every later run dies on the same row and no table after this one is ever
     * reached. The refused rows are isolated, marked `CONFLICT`, and the run carries on.
     */
    private suspend fun drainOutbox(): Pushed {
        val pending = table.pending()
        if (pending.isEmpty()) return Pushed(accepted = 0)

        val reconciled = table.reconcileBeforePush(pending)
        // Bytes first, always. A row that names an object the server does not have is
        // worse than a row that has not synced yet.
        val ready = table.uploadBlobs(reconciled)

        val stored = try {
            table.push(ready)
        } catch (rejection: Exception) {
            val permanent = (rejection as? SyncRejection)?.takeIf { it.isPermanent } ?: throw rejection
            return refuse(ready, permanent)
        }
        if (stored.isEmpty()) return Pushed(accepted = 0)

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
        return Pushed(accepted = accepted)
    }

    /**
     * Work out which of [rows] the server actually refused, and take those out of the outbox.
     *
     * PostgREST answers a batch on its first violation, so a rejected batch says nothing
     * about the rows after the bad one. Anything longer than a single row is therefore sent
     * again one row at a time: the good ones sync as usual, and only the ones that are
     * refused on their own are marked `CONFLICT`.
     *
     * A row that fails **transiently** during isolation is left alone and the throw is
     * allowed out. That is a network problem rather than a verdict, and it stops the run in
     * the usual way.
     */
    private suspend fun refuse(rows: List<Dto>, rejection: SyncRejection): Pushed {
        if (rows.size == 1) {
            database.transaction { table.markConflict(table.idOf(rows.single())) }
            telemetry.refused(entity, count = 1, status = rejection.status)
            return Pushed(accepted = 0, refusal = refusalOf(rejection, refused = 1))
        }

        var accepted = 0
        var refused = 0
        var lastStatus = rejection.status
        rows.forEach { row ->
            val stored = try {
                table.push(listOf(row))
            } catch (single: Exception) {
                val permanent = (single as? SyncRejection)?.takeIf { it.isPermanent } ?: throw single
                lastStatus = permanent.status
                database.transaction { table.markConflict(table.idOf(row)) }
                refused++
                return@forEach
            }
            val version = stored.firstOrNull()?.let(table::updatedAtOf)?.toString() ?: return@forEach
            database.transaction { table.markSynced(table.idOf(row), version) }
            accepted++
        }

        if (refused > 0) telemetry.refused(entity, count = refused, status = lastStatus)
        return Pushed(
            accepted = accepted,
            refusal = if (refused > 0) refusalOf(rejection, refused, lastStatus) else null,
        )
    }

    /** What the debug row shows for an entity carrying refused rows. */
    private fun refusalOf(rejection: SyncRejection, refused: Int, status: Int = rejection.status) =
        "${rejection::class.simpleName}($status, refused $refused)"

    /** How far a pull got: how many rows it saw, and the newest server timestamp among them. */
    private data class Pulled(val count: Int, val highWater: Instant?)

    /**
     * Apply the server's changes since the cursor, resolving conflicts by last-write-wins.
     *
     * The rows and the cursor move in the same transaction. A pull killed halfway leaves a
     * cursor that matches the rows actually written, so the next run simply re-fetches the
     * rest.
     */
    private suspend fun applyRemoteChanges(synchronizer: Synchronizer): Pulled {
        val cursor = synchronizer.cursor(entity).lastPulledAt
        // `gte` with an overlap rather than `gt`. Two rows can share an `updated_at`, and
        // clock skew between the app and Postgres is real; re-reading a few rows costs
        // nothing because applying one is idempotent, while missing one is permanent loss.
        val remote = when (val fetched = table.fetch(cursor?.minus(OVERLAP))) {
            is FetchResult.Rows -> fetched.rows
            // A table that cannot name its scope has not established that the server has
            // nothing — it has failed to ask. Throwing puts it in the same place as a
            // timeout: cursor untouched, entity recorded as failed, run reported Partial,
            // which is a retry. Reporting success here is the bug in issue #312.
            is FetchResult.ScopeMissing -> {
                telemetry.scopeMissing(entity, fetched.key)
                throw ScopeMissingException(entity, fetched.key)
            }
        }
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
        const val PHASE_PUSH = "push"
        const val PHASE_PULL = "pull"
    }
}
