package com.hopcape.odo.core.sync

/**
 * Runs one reconciliation pass: walks the [Syncable]s in [SyncEntity] order and reports
 * what happened.
 *
 * Sequential, not parallel — Now in Android fans its repositories out with `awaitAll`
 * because its entities are independent; Odo's are not on the way *up*. A service log whose
 * car the server has never seen is an FK violation, so a failing parent stops the **push**
 * phase rather than letting children fail noisily behind it. The **pull** phase has no such
 * ordering requirement and does not stop, because one entity failing must not blank every
 * entity after it (issue #312).
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

    /**
     * Something failed at [failedAt] and earlier entities kept their progress.
     *
     * [failedAt] is the *first* failure of the run. The pull phase carries on past it, so
     * later entities may have failed too — the field names where to start looking, not the
     * only thing that went wrong.
     */
    data class Partial(val failedAt: SyncEntity, val cause: Throwable?) : SyncResult

    /**
     * The run never started.
     *
     * [retryable] is what the platform scheduler branches on. `false` means signing in or
     * going online is what changes the answer, and waking a worker until then would burn
     * battery for nothing (SYNC_DESIGN §9). `true` means the refusal was about this moment
     * — a token that would not refresh, a maintenance window — and recording it as done is
     * how an install ends up with its initial pull never having happened.
     */
    data class Skipped(val reason: String, val retryable: Boolean) : SyncResult
}
