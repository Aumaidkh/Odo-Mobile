package com.hopcape.odo.core.platform.sync

import com.hopcape.odo.core.sync.SyncEngine
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.core.sync.observability.SyncTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs sync in-process: debounced, coalesced, and never twice at once.
 *
 * This is the whole scheduler on iOS, and on Android it is what the WorkManager worker ends
 * up calling. Two behaviours matter and both are about *not* running:
 *
 *  - **Debounce.** Logging three service entries in a row is one sync, not three. A local
 *    write waits [debounce] before it triggers anything, and a second write inside that
 *    window resets the timer rather than adding a run.
 *  - **Coalesce.** While a run is in flight, another request does nothing. The outbox is
 *    read at the start of a run, so a row written mid-run is simply picked up by the next
 *    one — and two concurrent runs would push the same rows twice and race on the cursor.
 *
 * A manual refresh skips the debounce, because someone is watching the spinner. It still
 * will not start a second concurrent run.
 *
 * There is no retry loop here. A failed run leaves its rows `PENDING`, and the next trigger
 * picks them up; on Android WorkManager's backoff supplies the pacing (SYNC_DESIGN §10).
 */
internal class CoroutineSyncScheduler(
    /**
     * Resolved lazily, inside the coroutine. Taking a [SyncEngine] directly would build it —
     * and therefore every repository and the database — while the scheduler is constructed,
     * which is during startup on the main thread.
     */
    private val engine: () -> SyncEngine,
    private val scope: CoroutineScope,
    private val telemetry: SyncTelemetry,
    private val debounce: Duration = DEFAULT_DEBOUNCE,
) : SyncScheduler {

    private var pending: Job? = null
    private var running: Job? = null

    override fun scheduleStartupSync() {
        telemetry.requested(REASON_STARTUP)
        trigger(delay = Duration.ZERO)
    }

    /**
     * Logged before the branch, so the record of *who asked* survives a run that never
     * happens. A request that is debounced away, or dropped because a run is already in
     * flight, leaves no other trace at all (issue #312).
     */
    override fun requestSync(reason: SyncReason) {
        telemetry.requested(reason.name)
        when (reason) {
            // The only debounced trigger. Everything else is either a person waiting or an
            // event that has already been coalesced by whatever produced it.
            SyncReason.LocalWrite -> trigger(delay = debounce)
            SyncReason.Manual,
            SyncReason.AppForeground,
            SyncReason.RemoteChange,
            SyncReason.Reconnected,
            SyncReason.SignIn,
            -> trigger(delay = Duration.ZERO)
        }
    }

    private fun trigger(delay: Duration) {
        // Restart the wait rather than queue a second one: a burst of writes should end in
        // exactly one run, [debounce] after the last of them.
        pending?.cancel()
        pending = scope.launch {
            if (delay > Duration.ZERO) delay(delay)
            if (running?.isActive == true) return@launch
            running = scope.launch { engine().sync() }
        }
    }

    private companion object {
        /** Long enough to absorb a burst of edits, short enough that nobody notices. */
        val DEFAULT_DEBOUNCE = 5.seconds

        /** `scheduleStartupSync` has no [SyncReason]; the log still needs to name it. */
        const val REASON_STARTUP = "Startup"
    }
}
