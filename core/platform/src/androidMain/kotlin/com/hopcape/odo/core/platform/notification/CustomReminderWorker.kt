package com.hopcape.odo.core.platform.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hopcape.logging.api.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Posts one of the owner's own reminders when its day arrives.
 *
 * Carries the title rather than reading the reminder back, for the same reason the document
 * worker does: it runs days or weeks after it was enqueued, and a read at that moment can
 * fail for reasons that have nothing to do with the reminder being due. A reminder edited or
 * deleted in the meantime is handled by the scheduler, which rebuilds everything on every
 * write.
 */
internal class CustomReminderWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters), KoinComponent {

    private val logger: Logger by inject()
    private val scheduler: CustomReminderScheduler by inject()

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_REMINDER_ID)
        val title = inputData.getString(KEY_TITLE)
        if (id == null || title.isNullOrBlank()) {
            // A bug in the scheduler rather than a condition that improves on its own.
            logger.error(TAG, "custom_reminder_incomplete")
            return Result.failure()
        }

        val posted = CustomReminderNotification.show(applicationContext, reminderId = id, title = title)
        logger.info(TAG, if (posted) "custom_reminder_shown" else "custom_reminder_suppressed")
        // A repeating reminder has no job for its next occurrence until something asks for
        // one. Nothing else will on a device the owner does not open, so this run does it.
        scheduler.refresh()
        return Result.success()
    }

    companion object {
        private const val TAG = "REMINDERS"

        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_TITLE = "title"
    }
}
