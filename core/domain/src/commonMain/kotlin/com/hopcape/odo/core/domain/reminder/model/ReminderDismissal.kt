package com.hopcape.odo.core.domain.reminder.model

import kotlinx.datetime.LocalDate

/**
 * One nudge the owner waved away — "not this one", not "never again".
 *
 * The feed skips a dismissed occurrence and surfaces the next one: dismissing the
 * 30-days-left insurance nudge leaves the 7-days one intact, and dismissing this
 * fortnight's air-pressure check leaves the next fortnight's. Turning a reminder off
 * for good is a different action (a preference topic, or pausing the custom reminder).
 *
 * Keyed on ([kind], [dueOn]) — the same key as the server's `uq_reminders_dedupe`
 * index — so when sync lands, a dismissal becomes a `reminders` row with status
 * `dismissed` and the server's own generator cannot re-create the nudge under it.
 * [customId] widens the key for [ReminderKind.CUSTOM], where two reminders can share a
 * due date; the server index ignores custom rows for the same reason.
 */
data class ReminderDismissal(
    val kind: ReminderKind,
    /** The day the dismissed nudge was due — the occurrence, not the deadline. */
    val dueOn: LocalDate,
    /** Which custom reminder, when [kind] is [ReminderKind.CUSTOM]; null otherwise. */
    val customId: ReminderId? = null,
) {
    init {
        require((kind == ReminderKind.CUSTOM) == (customId != null)) {
            "customId is required exactly when kind is CUSTOM"
        }
    }
}
