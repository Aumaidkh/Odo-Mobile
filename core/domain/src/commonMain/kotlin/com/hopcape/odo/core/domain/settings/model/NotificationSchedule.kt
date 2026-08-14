package com.hopcape.odo.core.domain.settings.model

import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.policy.DocumentReminderPolicy

/**
 * *When* Odo gets in touch, as opposed to
 * [NotificationPreferences], which is *whether* it does.
 *
 * The two are separate because they fail differently. An owner who finds a 30-day insurance
 * warning too early has no lever today except turning the whole topic off — and then they
 * miss the expiry, which is the outcome the app exists to prevent. A lead time they can move
 * is what keeps the topic on.
 *
 * Device-local like the rest of [AppSettings]: the schedule is honoured by this phone's
 * WorkManager jobs, so it belongs to the phone. It moves onto `profiles` on the day a server
 * sends the reminders instead.
 */
data class NotificationSchedule(
    /**
     * How many days before expiry each kind of paper is worth a nudge, longest lead first.
     *
     * A type missing from the map falls back to [DocumentReminderPolicy]'s own table, so a
     * document type added later starts with the lead the product chose for it rather than
     * with silence. An **empty list** is the owner saying "not for this one", and is
     * deliberately different from absent.
     */
    val documentLeadDays: Map<DocumentType, List<Int>> = emptyMap(),
    /**
     * The hour of the day a reminder fires, 0–23.
     *
     * One hour for the whole app rather than one per topic: an owner who wants to be told at
     * 8 AM wants it for everything, and a per-topic time is a setting nobody has asked for.
     * Minutes are not offered — the day is what matters about a reminder, and the choice
     * would only be false precision on a schedule the OS may shift by minutes anyway.
     */
    val notifyAtHour: Int = DEFAULT_NOTIFY_HOUR,
) {

    /** The owner's leads for [type], or the product's own when they have not chosen. */
    fun leadDaysFor(type: DocumentType): List<Int> =
        documentLeadDays[type] ?: DocumentReminderPolicy.defaultLeadDaysFor(type)

    /** [documentLeadDays] with [type] set to [days], sorted longest lead first. */
    fun withLeadDays(type: DocumentType, days: List<Int>): NotificationSchedule =
        copy(documentLeadDays = documentLeadDays + (type to days.distinct().sortedDescending()))

    companion object {
        /** 9 AM: after the morning commute, early enough to act on the same day. */
        const val DEFAULT_NOTIFY_HOUR: Int = 9

        /** The hours the settings screen offers, from a workable morning to a late evening. */
        val SELECTABLE_HOURS: List<Int> = listOf(6, 7, 8, 9, 10, 12, 15, 18, 20, 21)

        /** What a device has before the owner changes anything. */
        val Default: NotificationSchedule = NotificationSchedule()
    }
}
