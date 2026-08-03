package com.hopcape.odo.core.sync

import com.hopcape.odo.core.sync.observability.SyncTelemetry
import kotlin.time.TimeSource

/**
 * Runs the [Syncable]s in dependency order, one at a time, and stops at the first refusal.
 *
 * **Sequential, not concurrent.** Now in Android runs its syncers with `awaitAll`, which is
 * right when entities are independent. Odo's are not: `service_logs` references `cars`,
 * which references `profiles`, and a child pushed before its parent is a foreign-key error
 * on the server. [SyncEntity]'s declaration order *is* the dependency order, and this walks
 * it top to bottom.
 *
 * **A refusal stops the run**, it does not skip to the next entity. If `cars` could not be
 * pushed, every service log referencing a new car will fail too — carrying on would turn
 * one failure into six and leave the log full of consequences instead of the cause. The
 * rows stay `PENDING`, so the next run picks up exactly where this one stopped.
 *
 * The engine has no retry loop of its own. A [SyncResult.Partial] tells the scheduler to
 * retry, and the scheduler's backoff decides when — one retry policy, in one place.
 *
 * Design: [docs/SYNC_DESIGN.md] §5, §8.
 */
internal class DefaultSyncEngine(
    syncables: List<Syncable>,
    private val synchronizer: Synchronizer,
    private val telemetry: SyncTelemetry,
    private val gate: SyncGate,
    private val observer: SyncRunObserver,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : SyncEngine {

    /**
     * Sorted once at construction. Koin hands these over in registration order, which is
     * whatever order the modules happened to be listed in — not something the engine's
     * correctness should depend on.
     */
    private val ordered: List<Syncable> = syncables.sortedBy { it.entity.ordinal }

    override suspend fun sync(): SyncResult {
        if (!gate.canSync()) {
            telemetry.skipped(NOT_ALLOWED)
            return SyncResult.Skipped(NOT_ALLOWED)
        }
        if (ordered.isEmpty()) {
            telemetry.skipped(NOTHING_REGISTERED)
            return SyncResult.Skipped(NOTHING_REGISTERED)
        }

        val started = timeSource.markNow()
        val run = telemetry.startRun()
        observer.onRunning(true)
        // Every exit below has to clear it, including the early returns — a spinner left
        // running is a bug report.
        try {
            ordered.forEach { syncable ->
                val outcome = telemetry.entity(syncable.entity, run) { syncable.attempt() }

                if (outcome is Outcome.Stopped) {
                    outcome.cause?.let { synchronizer.recordFailure(syncable.entity, it) }
                    telemetry.stopped(run, syncable.entity, outcome.cause, started.elapsedNow().inWholeMilliseconds)
                    return SyncResult.Partial(failedAt = syncable.entity, cause = outcome.cause)
                }
            }

            telemetry.completed(run, ordered.size, started.elapsedNow().inWholeMilliseconds)
            return SyncResult.Success
        } finally {
            observer.onRunning(false)
        }
    }

    /**
     * One entity's turn, with a thrown exception folded into the same answer as a refusal.
     *
     * A `Syncable` is expected to report failure by returning `false`. One that throws
     * instead is a bug in that `Syncable` rather than a reason to lose the run, so it is
     * caught here and treated as the refusal it amounts to. Cancellation is not a failure —
     * a run the app called off must not be recorded as one, and swallowing it would leave
     * the coroutine running after its scope was cancelled.
     */
    private suspend fun Syncable.attempt(): Outcome =
        try {
            if (syncWith(synchronizer)) Outcome.Accepted else Outcome.Stopped(cause = null)
        } catch (cancellation: kotlin.coroutines.cancellation.CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Outcome.Stopped(cause = e)
        }

    private sealed interface Outcome {
        data object Accepted : Outcome
        data class Stopped(val cause: Throwable?) : Outcome
    }

    private companion object {
        const val NOT_ALLOWED = "not signed in"
        const val NOTHING_REGISTERED = "no syncables registered"
    }
}
