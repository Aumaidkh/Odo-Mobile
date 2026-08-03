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
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

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
 * A manual refresh uses `REPLACE` and no delay — someone is watching, so it cancels the
 * waiting job and goes now.
 */
internal class WorkManagerSyncScheduler(context: Context) : SyncScheduler {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun scheduleStartupSync() = enqueue(delaySeconds = 0, policy = ExistingWorkPolicy.KEEP)

    override fun requestSync(reason: SyncReason) = when (reason) {
        // The debounced one. Everything else has already been coalesced by whatever caused it.
        SyncReason.LocalWrite -> enqueue(DEBOUNCE.inWholeSeconds, ExistingWorkPolicy.KEEP)
        // Bypasses a pending job rather than waiting behind it.
        SyncReason.Manual -> enqueue(delaySeconds = 0, policy = ExistingWorkPolicy.REPLACE)
        SyncReason.AppForeground,
        SyncReason.RemoteChange,
        SyncReason.SignIn,
        -> enqueue(delaySeconds = 0, policy = ExistingWorkPolicy.KEEP)
    }

    private fun enqueue(delaySeconds: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<OdoSyncWorker>()
            .setConstraints(
                // Not "unmetered": an owner on mobile data still wants their service log
                // backed up, and the payloads are rows, not photos.
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            // Driven by the worker returning retry(). Exponential so a server that is down
            // is not hammered by every install at once.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    private companion object {
        /** Unique, so there is at most one sync pending or running for this install. */
        const val WORK_NAME = "OdoSync"
        const val BACKOFF_SECONDS = 30L
        val DEBOUNCE = 5.seconds
    }
}
