package com.hopcape.odo.core.platform.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.core.sync.observability.SyncTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * Android's scheduler: one unique WorkManager job called `OdoSync`.
 *
 * WorkManager rather than a coroutine on an app-lifetime scope, because sync has to survive
 * the app being backgrounded or killed mid-push, and it should wait for a network instead of
 * failing without one. Both are constraints WorkManager already enforces, and neither is
 * something a scope in the app process can promise.
 *
 * **Unique work + `KEEP` is the debounce.** A local write enqueues the job with a short
 * initial delay; a second write while that job is still pending is dropped, because the name
 * is already taken. Three service entries logged in a row become one sync, with no timer of
 * our own (SYNC_DESIGN §10).
 *
 * A manual refresh and a fresh sign-in use `REPLACE` and no delay — someone is watching, so
 * they cancel the waiting job and go now.
 *
 * **How long everything else waits is [SyncJitter]'s decision, not this class's.** The
 * triggers that matter reach every install in the same second, and a fixed delay would only
 * move the spike rather than flatten it.
 *
 * **Every request is logged, and so is what WorkManager then does with the job.** Enqueuing
 * is not running: a job whose network constraint is unmet sits there, and from inside the app
 * that is indistinguishable from a job that was never created — both produce no sync log at
 * all, which is exactly what the reports on issue #312 looked like. [SyncTelemetry.requested]
 * says somebody asked, and [SyncTelemetry.workState] says what came of it.
 */
internal class WorkManagerSyncScheduler(
    context: Context,
    private val telemetry: SyncTelemetry,
    /** How long this install waits, so a shared trigger does not become one spike. */
    private val jitter: SyncJitter = SyncJitter(),
    /**
     * Owns its own scope rather than borrowing one: the scheduler outlives every screen, and
     * the only thing launched on it is the diagnostic collector below. A `SupervisorJob` keeps
     * a failure there from taking anything else with it.
     */
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : SyncScheduler {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    init {
        scope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME)
                // No work info at all is itself an answer — it means nothing is queued.
                .map { infos ->
                    val info = infos.firstOrNull()
                    (info?.state?.name ?: NO_WORK) to (info?.runAttemptCount ?: 0)
                }
                .distinctUntilChanged()
                .collect { (state, attempts) -> telemetry.workState(state, attempts) }
        }
    }

    override fun scheduleStartupSync() {
        telemetry.requested(REASON_STARTUP)
        enqueue(jitter.startupDelay(), ExistingWorkPolicy.KEEP)
    }

    override fun requestSync(reason: SyncReason) {
        telemetry.requested(reason.name)
        // The delay, including the local-write debounce, is the jitter policy's to decide —
        // it is the one place that knows which triggers arrive at every install at once.
        val delay = jitter.initialDelay(reason)
        when (reason) {
            // Bypasses a pending job rather than waiting behind it.
            SyncReason.Manual -> enqueue(delay, ExistingWorkPolicy.REPLACE)
            // Also REPLACE, and for a sharper reason: any job already waiting was queued while
            // there was no session, so its backoff was earned by failures that signing in just
            // fixed. KEEP would make the owner wait out a penalty for the very thing they have
            // now done — up to hours, while the screen says their data is backed up.
            SyncReason.SignIn -> enqueue(delay, ExistingWorkPolicy.REPLACE)
            // KEEP is the debounce: a second request while one is pending is dropped, because
            // the name is already taken.
            SyncReason.LocalWrite,
            SyncReason.AppForeground,
            SyncReason.RemoteChange,
            SyncReason.Reconnected,
            -> enqueue(delay, ExistingWorkPolicy.KEEP)
        }
    }

    private fun enqueue(delay: Duration, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<OdoSyncWorker>()
            .setConstraints(
                // Not "unmetered": an owner on mobile data still wants their service log
                // backed up, and the payloads are rows, not photos.
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setInitialDelay(delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            // Driven by the worker returning retry(). Exponential so a server that is down is
            // given room, and off a base drawn per request so that two installs failing
            // together climb different curves instead of returning in the same second.
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                jitter.backoffBase().inWholeMilliseconds,
                TimeUnit.MILLISECONDS,
            )
            .build()

        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    private companion object {
        /** Unique, so there is at most one sync pending or running for this install. */
        const val WORK_NAME = "OdoSync"

        /** `scheduleStartupSync` has no [SyncReason]; the log still needs to name it. */
        const val REASON_STARTUP = "Startup"
        const val NO_WORK = "NONE"
    }
}
