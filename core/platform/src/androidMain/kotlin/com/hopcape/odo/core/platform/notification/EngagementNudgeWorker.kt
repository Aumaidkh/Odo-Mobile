package com.hopcape.odo.core.platform.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.platform.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Posts a nudge when its day arrives.
 *
 * The copy is resolved here rather than carried in the job's data, unlike the reminder
 * workers: theirs is the owner's own words and can only come from the row, while this is
 * Odo's own and is the same sentence whenever it runs.
 */
internal class EngagementNudgeWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters), KoinComponent {

    private val logger: Logger by inject()

    override suspend fun doWork(): Result {
        val nudge = inputData.getString(KEY_NUDGE)
            ?.let { name -> EngagementNudge.entries.firstOrNull { it.name == name } }
        if (nudge == null) {
            // A bug in the scheduler rather than a condition that improves on its own.
            logger.error(TAG, "nudge_unknown")
            return Result.failure()
        }

        val posted = EngagementNudgeNotification.show(
            context = applicationContext,
            nudge = nudge.name,
            title = applicationContext.getString(nudge.titleRes()),
            body = applicationContext.getString(nudge.bodyRes()),
        )
        logger.info(TAG, if (posted) "nudge_shown_${nudge.name}" else "nudge_suppressed_${nudge.name}")
        return Result.success()
    }

    private fun EngagementNudge.titleRes() = when (this) {
        EngagementNudge.FIRST_SCORE -> R.string.nudge_first_score_title
        EngagementNudge.SCAN_FOLLOW_UP -> R.string.nudge_scan_follow_up_title
    }

    private fun EngagementNudge.bodyRes() = when (this) {
        EngagementNudge.FIRST_SCORE -> R.string.nudge_first_score_body
        EngagementNudge.SCAN_FOLLOW_UP -> R.string.nudge_scan_follow_up_body
    }

    companion object {
        private const val TAG = "NUDGES"

        const val KEY_NUDGE = "nudge"
    }
}
