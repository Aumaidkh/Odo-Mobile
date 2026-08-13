package com.hopcape.odo.core.platform.notification

/**
 * Puts the owner's own reminders on the device's notification schedule.
 *
 * The counterpart of [DocumentReminderScheduler] for the topics the owner writes themselves —
 * "check tyre pressure every 15 days", "renew the FASTag in March". These have always been
 * derivable and shown in the feed, and until now nothing delivered them: an owner who did not
 * open the app heard nothing about a reminder they set specifically so they would not have to.
 *
 * Same one-method shape, and for the same reason: callers say "the reminders changed" after a
 * write and the whole schedule is rebuilt from the database, so an edited, paused or deleted
 * reminder cannot leave a nudge behind it.
 *
 * Best-effort by contract: implementations swallow their own failures rather than throw. A
 * reminder that saved but could not be scheduled is still a saved reminder.
 */
fun interface CustomReminderScheduler {

    /** Rebuild the whole schedule from the reminders currently on file. */
    suspend fun refresh()
}
