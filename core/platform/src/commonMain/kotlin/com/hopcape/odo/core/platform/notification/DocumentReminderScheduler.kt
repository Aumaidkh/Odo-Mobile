package com.hopcape.odo.core.platform.notification

/**
 * Puts the reminders a car's documents have earned on the device's notification schedule.
 *
 * The reminders screen computes its list every time it is read, which is enough to *show* an
 * expiry but not to tell anyone about it — an owner who does not open the app hears nothing.
 * This is the half that reaches them: the same rule
 * ([com.hopcape.odo.core.domain.document.policy.DocumentReminderPolicy]) resolved into
 * notifications the OS will deliver on the day.
 *
 * One method, and it recomputes everything. Callers do not track which reminder belongs to
 * which document — they say "the documents changed" after a write, and what is on the
 * schedule is rebuilt from what is in the database. A renewed, re-dated or deleted document
 * therefore cannot leave a stale nudge behind.
 *
 * Best-effort by contract: implementations swallow their own failures rather than throw. A
 * document that saved but could not be scheduled is still a saved document, and failing the
 * write over a notification would lose the owner's data.
 */
fun interface DocumentReminderScheduler {

    /** Rebuild the whole schedule from the documents currently on file. */
    suspend fun refresh()
}
