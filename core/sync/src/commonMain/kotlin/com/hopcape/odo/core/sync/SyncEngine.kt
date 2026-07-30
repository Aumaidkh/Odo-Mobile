package com.hopcape.odo.core.sync

/**
 * Runs one reconciliation pass: walks the [Syncable]s in [SyncEntity] order and reports
 * what happened.
 *
 * Sequential, not parallel — Now in Android fans its repositories out with `awaitAll`
 * because its entities are independent; Odo's are not. A service log whose car the server
 * has never seen is an FK violation, so a failing parent **stops the run** rather than
 * letting children fail noisily behind it.
 *
 * The engine never decides *when* to run (that's [SyncScheduler]) and never retries
 * (that's the platform scheduler's backoff). It runs once, honestly reports the outcome,
 * and returns.
 *
 * Design: [docs/SYNC_DESIGN.md] §5, §8.
 */
interface SyncEngine {
    suspend fun sync(): SyncResult
}

/**
 * The outcome of one run. [Partial] exists because "some entities reconciled, then one
 * failed" is the common real-world case and is genuinely different from total failure:
 * the work that landed is committed and keeps its cursor, only the rest is retried.
 */
sealed interface SyncResult {

    /** Every entity reconciled. */
    data object Success : SyncResult

    /** Reconciled up to [failedAt], which failed; earlier entities kept their progress. */
    data class Partial(val failedAt: SyncEntity, val cause: Throwable?) : SyncResult

    /**
     * The run never started — no session, or no usable connectivity. Not a failure and
     * **not retry-worthy**: signing in and going online are what change this, and the app
     * is fully functional offline in the meantime (SYNC_DESIGN §9).
     */
    data class Skipped(val reason: String) : SyncResult
}
