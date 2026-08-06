package com.hopcape.odo.core.domain.reminder.policy

import com.hopcape.odo.core.domain.reminder.model.CustomReminder
import com.hopcape.odo.core.domain.reminder.model.ReminderCadence
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.plus

/**
 * When a custom reminder fires next.
 *
 * A pure domain service — no repository, no clock. It answers "given this cadence,
 * which occurrence is next?" and nothing else; the feed decides how to show it and the
 * scheduler (M4) decides how to deliver it.
 *
 * Missed occurrences are skipped, never queued: an air-pressure check the owner slept
 * through for a month yields the next upcoming date, not four stale nudges. This is the
 * same rule [DocumentReminderPolicy][com.hopcape.odo.core.domain.document.policy.DocumentReminderPolicy]
 * applies to lead days — a reminder fired late is worse than none.
 */
object CadencePlanner {

    /**
     * The next occurrence of [reminder] on or after [from], or `null` when nothing is
     * ahead — a paused reminder, or a one-off whose day has passed.
     */
    fun nextOccurrence(
        reminder: CustomReminder,
        from: LocalDate,
    ): ReminderOccurrence? =
        if (reminder.paused) {
            null
        } else {
            nextOccurrence(reminder.cadence, reminder.startsOn, from, reminder.anchorKm)
        }

    /**
     * The cadence rule itself. [from] is the first day an occurrence may fall on —
     * today for the ordinary read, or the day after a dismissed occurrence to find the
     * one behind it.
     */
    fun nextOccurrence(
        cadence: ReminderCadence,
        startsOn: LocalDate,
        from: LocalDate,
        anchorKm: Int? = null,
    ): ReminderOccurrence? = when (cadence) {
        ReminderCadence.Once ->
            if (startsOn >= from) ReminderOccurrence.OnDate(startsOn) else null

        is ReminderCadence.EveryDays -> ReminderOccurrence.OnDate(
            if (from <= startsOn) {
                startsOn
            } else {
                val elapsed = startsOn.daysUntil(from)
                val steps = (elapsed + cadence.days - 1) / cadence.days
                startsOn.plus(steps * cadence.days, DateTimeUnit.DAY)
            },
        )

        ReminderCadence.Monthly -> ReminderOccurrence.OnDate(
            if (from <= startsOn) {
                startsOn
            } else {
                // Each candidate is computed from startsOn, not from the previous
                // candidate, so a 31st clamped to February does not stay clamped in March.
                val steps = startsOn.monthsUntil(from)
                val candidate = startsOn.plus(steps, DateTimeUnit.MONTH)
                if (candidate >= from) candidate else startsOn.plus(steps + 1, DateTimeUnit.MONTH)
            },
        )

        // The target never advances by itself: a reached target stays due until the
        // owner acts (edit, pause, or the M4 mark-done that re-anchors). Sliding it a
        // step ahead of the odometer would mean the nudge can never fire at all.
        is ReminderCadence.EveryDistance -> anchorKm?.let { anchor ->
            ReminderOccurrence.AtOdometer(anchor + cadence.km)
        }
    }
}

/**
 * One point at which a reminder fires: a day on the calendar, or an odometer reading
 * for the distance-based cadences.
 *
 * Two shapes rather than a forced date, because a distance target has no date — the
 * car reaches it whenever it is driven there, and pretending otherwise would put a
 * made-up day on screen.
 */
sealed interface ReminderOccurrence {
    data class OnDate(val date: LocalDate) : ReminderOccurrence
    data class AtOdometer(val targetKm: Int) : ReminderOccurrence
}
