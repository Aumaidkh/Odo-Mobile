package com.hopcape.odo.core.platform.logging

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.hopcape.logging.api.LogUploadScheduler
import java.util.concurrent.TimeUnit

/**
 * Android's scheduler for [LogUploadWorker] — two unique WorkManager jobs, matching the
 * two triggers docs/LOGGING_PLAN.md §7.2 defines:
 *
 *  - `OdoLogUploadPeriodic`, every 6 hours, `UNMETERED` + battery-not-low. Left running
 *    unconditionally — [com.hopcape.logging.internal.upload.LogUploadCoordinator] is what
 *    actually gates on consent, per run, so there is nothing to toggle here when consent
 *    changes; the next scheduled run just starts working once it is granted.
 *  - `OdoLogUploadNow`, one-shot, `CONNECTED` only — "Send diagnostics" is an explicit user
 *    action and bypasses consent by construction (D3), so it only needs a network, not
 *    Wi-Fi specifically.
 */
internal class WorkManagerLogUploadScheduler(context: Context) : LogUploadScheduler {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<LogUploadWorker>(PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setInputData(workDataOf(LogUploadWorker.KEY_IS_MANUAL to false))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        // KEEP: a periodic job already scheduled has the same cadence and constraints —
        // nothing about calling this again (e.g. on every app launch) should reset it.
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun requestUploadNow() {
        val request = OneTimeWorkRequestBuilder<LogUploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(LogUploadWorker.KEY_IS_MANUAL to true))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        // REPLACE: someone is watching (the diagnostics screen), so this bypasses whatever
        // — if anything — was already waiting, the same reasoning as a manual sync refresh.
        workManager.enqueueUniqueWork(NOW_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(NOW_WORK_NAME)
    }

    private companion object {
        const val PERIODIC_WORK_NAME = "OdoLogUploadPeriodic"
        const val NOW_WORK_NAME = "OdoLogUploadNow"
        const val PERIODIC_INTERVAL_HOURS = 6L
        const val BACKOFF_SECONDS = 30L
    }
}
