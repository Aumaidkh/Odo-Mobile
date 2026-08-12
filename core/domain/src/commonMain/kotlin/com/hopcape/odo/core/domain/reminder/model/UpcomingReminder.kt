package com.hopcape.odo.core.domain.reminder.model

import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.reminder.policy.ReminderOccurrence
import com.hopcape.odo.core.domain.servicelog.policy.ServiceDueStatus
import kotlinx.datetime.LocalDate

/**
 * How soon an upcoming reminder needs the owner — what picks its list bucket and pill.
 *
 * [DUE_THIS_WEEK] is the screen's "This week" section; the other two share the
 * "Upcoming" section with an amber or green pill.
 */
enum class ReminderUrgency { DUE_THIS_WEEK, DUE_SOON, ON_TRACK }

/**
 * One row of the reminders feed — something ahead of the owner, whatever its source.
 *
 * Derived rows ([DocumentRenewal], [ServiceDue]) are computed from documents and
 * service history on every read and never stored; [Custom] wraps a stored
 * [CustomReminder] with its next occurrence. No copy here — each case carries the
 * numbers, and the screen writes its own line.
 *
 * [dismissalKey] is the occurrence a "not this one" tap records; `null` for rows with
 * no dated occurrence to wave away (a distance target has no date to key on, and an
 * on-track row offers no dismissal).
 */
sealed interface UpcomingReminder {
    val kind: ReminderKind
    val urgency: ReminderUrgency
    val dismissalKey: ReminderDismissal?

    /** A paper inside or ahead of its renewal window. Lapsed papers are the vault's
     *  business, not a countdown's, so they never appear here. */
    data class DocumentRenewal(
        override val kind: ReminderKind,
        val documentId: DocumentId,
        val type: DocumentType,
        val expiresOn: LocalDate,
        val daysLeft: Int,
        override val urgency: ReminderUrgency,
        /** The lead-day nudge a dismissal waves away — not the expiry itself. */
        val currentNudgeOn: LocalDate?,
    ) : UpcomingReminder {
        override val dismissalKey: ReminderDismissal?
            get() = currentNudgeOn?.let { ReminderDismissal(kind, it) }
    }

    /**
     * Where the car stands against its service interval. [status] carries the days/km
     * numbers; [dueOn] is the calendar day the time half of the interval ends, which is
     * also the occurrence a dismissal keys on.
     */
    data class ServiceDue(
        override val kind: ReminderKind,
        val status: ServiceDueStatus,
        val dueOn: LocalDate,
        override val urgency: ReminderUrgency,
    ) : UpcomingReminder {
        override val dismissalKey: ReminderDismissal
            get() = ReminderDismissal(kind, dueOn)
    }

    /** A custom reminder's next occurrence. */
    data class Custom(
        val reminder: CustomReminder,
        val occurrence: ReminderOccurrence,
        override val urgency: ReminderUrgency,
    ) : UpcomingReminder {
        override val kind: ReminderKind get() = ReminderKind.CUSTOM

        override val dismissalKey: ReminderDismissal?
            get() = (occurrence as? ReminderOccurrence.OnDate)
                ?.let { ReminderDismissal(kind, it.date, reminder.id) }
    }
}

/**
 * The reminders screen's whole answer, resolved against one day.
 *
 * [thisWeek] holds only [ReminderUrgency.DUE_THIS_WEEK] rows; [upcoming] the rest, most
 * urgent first. [suggestions] are the presets the owner has not set up — the "Remind
 * me" opt-in rows.
 */
data class ReminderFeed(
    val thisWeek: List<UpcomingReminder>,
    val upcoming: List<UpcomingReminder>,
    val suggestions: List<ReminderPreset>,
) {
    companion object {
        val Empty = ReminderFeed(
            thisWeek = emptyList(),
            upcoming = emptyList(),
            suggestions = ReminderPreset.entries,
        )
    }
}
