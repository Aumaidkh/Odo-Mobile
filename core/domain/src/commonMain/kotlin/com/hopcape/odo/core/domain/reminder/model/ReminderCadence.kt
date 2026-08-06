package com.hopcape.odo.core.domain.reminder.model

/**
 * How often a custom reminder recurs — the domain form of the create screen's repeat
 * options.
 *
 * Plain values with no validation of their own; [CustomReminder.create] is what rejects
 * a non-positive step, so every invariant on a reminder is checked in one place.
 */
sealed interface ReminderCadence {

    /** One nudge on the start date, then done. */
    data object Once : ReminderCadence

    /** Every [days] days from the start date (the screen's "every 15 days"). */
    data class EveryDays(val days: Int) : ReminderCadence

    /** The same day each month, clamped to shorter months (31st → 30th/28th). */
    data object Monthly : ReminderCadence

    /**
     * Every [km] kilometres of driving, counted from the odometer reading the reminder
     * was anchored at ([CustomReminder.anchorKm]). Triggers off distance, not the
     * calendar, so its occurrence is an odometer target rather than a date.
     */
    data class EveryDistance(val km: Int) : ReminderCadence
}
