package com.hopcape.odo.feature.reminders.domain.notification

import com.hopcape.odo.core.domain.reminder.model.CustomReminder
import com.hopcape.odo.core.domain.reminder.model.ReminderId

/**
 * Port for putting a custom reminder's next occurrence on the device's notification
 * schedule, and taking it off again.
 *
 * Custom reminders are local-only nudges: the server engine (pg_cron + FCM) dispatches
 * the derived kinds, but it has no generator for the owner's own topics, so these fire
 * from the device. The real implementation arrives with the M4 engine (platform
 * notifications / WorkManager); until then [NoOpReminderNotificationScheduler] keeps
 * the call sites honest.
 *
 * Best-effort by contract: implementations must swallow their own failures rather than
 * throw, because a reminder that saved but could not be scheduled is a saved reminder —
 * the feed still shows it, and failing the write over a notification would lose data.
 */
internal interface ReminderNotificationScheduler {

    /** Put [reminder]'s next occurrence on the schedule, replacing any older entry. */
    suspend fun schedule(reminder: CustomReminder)

    /** Take every scheduled occurrence of this reminder off the schedule. */
    suspend fun cancel(id: ReminderId)
}

/** Stands in until the M4 engine lands. Does nothing, on purpose. */
internal object NoOpReminderNotificationScheduler : ReminderNotificationScheduler {
    override suspend fun schedule(reminder: CustomReminder) = Unit
    override suspend fun cancel(id: ReminderId) = Unit
}
