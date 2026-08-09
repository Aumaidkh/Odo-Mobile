package com.hopcape.odo.core.platform.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hopcape.logging.api.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Posts one document-expiry reminder when its day arrives.
 *
 * The worker carries the few facts the notification needs rather than reading the database
 * again. It runs weeks after it was scheduled, and a read at that point could fail for
 * reasons — a locked device, a migration — that have nothing to do with the reminder being
 * due. What it cannot know is whether the document was renewed in the meantime; that is
 * handled by the scheduler, which cancels and rebuilds the whole schedule on every write.
 *
 * A missing notification permission is not a failure to retry: the owner said no, and the
 * post is simply dropped.
 *
 * Every run is logged. A reminder that never arrives is otherwise invisible from both sides —
 * the owner does not know one was due, and nothing in the app records that it was.
 *
 * The logger is resolved through [KoinComponent] because WorkManager constructs its own
 * workers; a custom `WorkerFactory` would be a second wiring path for one class.
 */
internal class DocumentReminderWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters), KoinComponent {

    private val logger: Logger by inject()

    override suspend fun doWork(): Result {
        val type = inputData.getString(KEY_TYPE)
        val expiresOn = inputData.getString(KEY_EXPIRES_ON)
        val documentId = inputData.getString(KEY_DOCUMENT_ID)
        val daysBefore = inputData.getInt(KEY_DAYS_BEFORE, 0)
        if (type == null || expiresOn == null || documentId == null) {
            // Nothing to retry: the job was enqueued without what it needs, which is a bug
            // in the scheduler rather than a condition that improves on its own.
            logger.error(TAG, "document_reminder_incomplete")
            return Result.failure()
        }

        val posted = DocumentReminderNotification.show(
            context = applicationContext,
            documentId = documentId,
            typeName = type,
            daysBefore = daysBefore,
            expiresOn = expiresOn,
        )
        logger.info(
            TAG,
            if (posted) "document_reminder_shown" else "document_reminder_suppressed",
            fields = mapOf("type" to type, "days_before" to daysBefore),
        )
        return Result.success()
    }

    companion object {
        private const val TAG = "REMINDERS"

        const val KEY_DOCUMENT_ID = "document_id"
        const val KEY_TYPE = "type"
        const val KEY_DAYS_BEFORE = "days_before"
        const val KEY_EXPIRES_ON = "expires_on"
    }
}
