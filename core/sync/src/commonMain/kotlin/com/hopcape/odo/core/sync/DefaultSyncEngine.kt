package com.hopcape.odo.core.sync

import com.hopcape.odo.core.sync.observability.SyncTelemetry
import kotlin.time.TimeSource

/**
 * Runs the [Syncable]s in dependency order, one at a time, in two phases: every push, then
 * every pull.
 *
 * **Sequential, not concurrent.** Now in Android runs its syncers with `awaitAll`, which is
 * right when entities are independent. Odo's are not on the way up: `service_logs` references
 * `cars`, which references `profiles`, and a child pushed before its parent is a foreign-key
 * error on the server. [SyncEntity]'s declaration order *is* the dependency order, and this
 * walks it top to bottom.
 *
 * **A refused push stops the push phase.** If `cars` could not be pushed, every service log
 * referencing a new car will fail too — carrying on would turn one failure into six and
 * leave the log full of consequences instead of the cause. The rows stay `PENDING`, so the
 * next run picks up exactly where this one stopped.
 *
 * **A refused pull does not stop anything.** Fetching is not ordered: `cars` can be pulled
 * whether or not `profiles` was, and the FK argument above simply does not apply to reading.
 * Stopping here is what put an owner with a full account in front of four first-run empty
 * states, because `PROFILES` is the first entity and everything else sat behind it (issue
 * #312). Every entity now gets its turn, and the run still reports [SyncResult.Partial] so
 * the scheduler retries what failed.
 *
 * Both phases run even when the push phase stopped early. The entities whose push was never
 * attempted have nothing pending by definition of not having been tried — their pull is
 * still worth doing, and it is the half the owner can see.
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
        when (val verdict = gate.evaluate()) {
            is SyncVerdict.NoSession -> return skip(verdict.reason, retryable = false)
            is SyncVerdict.Unavailable -> return skip(verdict.reason, retryable = true)
            SyncVerdict.Allowed -> Unit
        }
        if (ordered.isEmpty()) return skip(NOTHING_REGISTERED, retryable = false)

        val started = timeSource.markNow()
        val run = telemetry.startRun()
        observer.onRunning(true)
        // Every exit below has to clear it, including the early returns — a spinner left
        // running is a bug report.
        try {
            var failure: Failure? = null

            for (syncable in ordered) {
                val outcome = telemetry.entity(syncable.entity, PHASE_PUSH, run) {
                    syncable.attempt { pushTo(synchronizer) }
                }
                if (outcome is Outcome.Stopped) {
                    failure = record(syncable.entity, outcome)
                    break
                }
            }

            ordered.forEach { syncable ->
                val outcome = telemetry.entity(syncable.entity, PHASE_PULL, run) {
                    syncable.attempt { pullFrom(synchronizer) }
                }
                // The first failure of the run is the one worth naming: it is the cause, and
                // anything after it may only be a consequence.
                if (outcome is Outcome.Stopped) {
                    val recorded = record(syncable.entity, outcome)
                    if (failure == null) failure = recorded
                }
            }

            val elapsed = started.elapsedNow().inWholeMilliseconds
            val stopped = failure
            if (stopped != null) {
                telemetry.stopped(run, stopped.entity, stopped.cause, elapsed)
                return SyncResult.Partial(failedAt = stopped.entity, cause = stopped.cause)
            }
            telemetry.completed(run, ordered.size, elapsed)
            return SyncResult.Success
        } finally {
            observer.onRunning(false)
        }
    }

    private suspend fun skip(reason: String, retryable: Boolean): SyncResult {
        telemetry.skipped(reason, retryable)
        return SyncResult.Skipped(reason, retryable)
    }

    private suspend fun record(entity: SyncEntity, outcome: Outcome.Stopped): Failure {
        outcome.cause?.let { synchronizer.recordFailure(entity, it) }
        return Failure(entity, outcome.cause)
    }

    /**
     * One entity's turn at one phase, with a thrown exception folded into the same answer as
     * a refusal.
     *
     * A `Syncable` is expected to report failure by returning `false`. One that throws
     * instead is a bug in that `Syncable` rather than a reason to lose the run, so it is
     * caught here and treated as the refusal it amounts to. Cancellation is not a failure —
     * a run the app called off must not be recorded as one, and swallowing it would leave
     * the coroutine running after its scope was cancelled.
     */
    private suspend fun Syncable.attempt(half: suspend Syncable.() -> Boolean): Outcome =
        try {
            if (half()) Outcome.Accepted else Outcome.Stopped(cause = null)
        } catch (cancellation: kotlin.coroutines.cancellation.CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Outcome.Stopped(cause = e)
        }

    /** Where the run first went wrong, carried to the end so both phases still run. */
    private data class Failure(val entity: SyncEntity, val cause: Throwable?)

    private sealed interface Outcome {
        data object Accepted : Outcome
        data class Stopped(val cause: Throwable?) : Outcome
    }

    private companion object {
        const val NOTHING_REGISTERED = "no syncables registered"
        const val PHASE_PUSH = "push"
        const val PHASE_PULL = "pull"
    }
}
