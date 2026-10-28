package com.hopcape.odo.core.platform.notification

import android.content.Context
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.domain.servicelog.policy.ServiceReminder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Posts a planned service-due notification. Everything it needs is in the job's data. */
internal class ServiceDueReminderWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters), KoinComponent {

    private val logger: Logger by inject()
    private val telemetry: ServiceReminderTelemetry by inject()

    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND)?.let { name -> ServiceReminder.Kind.entries.firstOrNull { it.name == name } }
        val dueOn = inputData.getString(KEY_DUE_ON)
        if (kind == null || dueOn == null) {
            logger.error(TAG, "service_reminder_bad_input")
            return Result.failure()
        }
        // A refresh on the posting day re-enqueues it; one post per kind and due date.
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(kind.name, null) == dueOn) return Result.success()
        if (ServiceDueNotification.show(applicationContext, kind, dueOn)) {
            prefs.edit { putString(kind.name, dueOn) }
            telemetry.posted(kind, dueOn)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "REMINDERS"
        private const val PREFS_NAME = "odo_service_reminders"
        const val KEY_KIND = "kind"
        const val KEY_DUE_ON = "due_on"
    }
}
