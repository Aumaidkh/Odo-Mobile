package com.hopcape.odo.core.platform.notification

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.runCatchingCancellable
import java.util.concurrent.TimeUnit

/**
 * Nudges on WorkManager — the same machinery the reminder schedulers use, for the same
 * reasons: the day matters and the minute does not, no exact-alarm permission is needed, and
 * the schedule survives a reboot.
 *
 * One unique job per kind, replaced on every call. Scanning three bills in a week should
 * leave one nudge on the schedule, and the newest scan is the one worth counting from.
 */
internal class WorkManagerEngagementNudgeScheduler(
    context: Context,
    private val logger: Logger,
) : EngagementNudgeScheduler {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    override suspend fun schedule(nudge: EngagementNudge) {
        runCatchingCancellable {
            val request = OneTimeWorkRequestBuilder<EngagementNudgeWorker>()
                .setInitialDelay(nudge.delay.inWholeMinutes, TimeUnit.MINUTES)
                .setInputData(Data.Builder().putString(EngagementNudgeWorker.KEY_NUDGE, nudge.name).build())
                .build()
            workManager.enqueueUniqueWork(workName(nudge), ExistingWorkPolicy.REPLACE, request)
            logger.info(TAG, "nudge_scheduled_${nudge.name}")
        }.onFailure {
            // Best-effort by contract: a nudge that could not be scheduled must never cost
            // the owner the thing they were doing when it was earned.
            logger.warn(TAG, "nudge_schedule_failed_${nudge.name}")
        }
    }

    private fun workName(nudge: EngagementNudge) = "$WORK_PREFIX${nudge.name}"

    private companion object {
        const val TAG = "NUDGES"
        const val WORK_PREFIX = "odo_nudge_"
    }
}
