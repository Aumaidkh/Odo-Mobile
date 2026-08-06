package com.hopcape.odo.core.domain.reminder.analysis

import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.core.domain.document.policy.DocumentReminderPolicy
import com.hopcape.odo.core.domain.reminder.model.CustomReminder
import com.hopcape.odo.core.domain.reminder.model.ReminderDismissal
import com.hopcape.odo.core.domain.reminder.model.ReminderFeed
import com.hopcape.odo.core.domain.reminder.model.ReminderKind
import com.hopcape.odo.core.domain.reminder.model.ReminderPreset
import com.hopcape.odo.core.domain.reminder.model.ReminderUrgency
import com.hopcape.odo.core.domain.reminder.model.UpcomingReminder
import com.hopcape.odo.core.domain.reminder.policy.CadencePlanner
import com.hopcape.odo.core.domain.reminder.policy.ReminderOccurrence
import com.hopcape.odo.core.domain.servicelog.policy.ServiceDueStatus
import com.hopcape.odo.core.domain.servicelog.policy.ServiceIntervalPolicy
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Builds the reminders feed — everything ahead of the owner, bucketed by urgency.
 *
 * A pure domain service: it takes what the repositories read and answers with the
 * feed. It owns no rule of its own about *when* things are due — expiry windows come
 * from [DocumentValidity] and [DocumentReminderPolicy], service intervals from
 * [ServiceDueStatus], custom occurrences from [CadencePlanner] — so every surface that
 * asks those questions separately (Home's attention card, the vault, the health score)
 * stays in agreement with this one.
 *
 * Dismissals hide a single occurrence, not the reminder: a dismissed row comes back
 * when its next occurrence differs from the dismissed one. Notification preferences
 * deliberately do not gate the feed — they decide what is *pushed* (the scheduler's
 * business), and a list the owner opened should show the truth either way.
 */
object ReminderFeedPolicy {

    /** A calendar occurrence within this many days is "this week". */
    const val THIS_WEEK_DAYS: Int = 7

    /** A custom calendar occurrence within this many days earns the amber pill. */
    const val CUSTOM_DUE_SOON_DAYS: Int = 14

    /** A distance target within this many km of the odometer earns the amber pill. */
    const val DISTANCE_DUE_SOON_KM: Int = 500

    /**
     * @param documents the car's non-deleted documents.
     * @param serviceStatus where the car stands against its interval, from
     *   [ServiceIntervalPolicy][com.hopcape.odo.core.domain.servicelog.policy.ServiceIntervalPolicy].
     * @param customs the car's non-deleted custom reminders, paused ones included.
     * @param dismissals every dismissal recorded for the car.
     * @param today the day being read on — passed in, never read from a clock here.
     * @param currentKm the car's odometer reading today, for distance targets; `null`
     *   drops the distance urgency to on-track rather than guessing.
     */
    fun feedFor(
        documents: List<Document>,
        serviceStatus: ServiceDueStatus,
        customs: List<CustomReminder>,
        dismissals: List<ReminderDismissal>,
        today: LocalDate,
        currentKm: Int? = null,
    ): ReminderFeed {
        val dismissed = dismissals.toSet()
        val rows = buildList {
            addAll(documentRows(documents, today))
            serviceRow(serviceStatus, today)?.let(::add)
            addAll(customRows(customs, dismissed, today, currentKm))
        }.filterNot { it.dismissalKey in dismissed }

        val (thisWeek, upcoming) = rows
            .sortedWith(compareBy({ it.urgency.ordinal }, { it.sortKey(today) }))
            .partition { it.urgency == ReminderUrgency.DUE_THIS_WEEK }

        return ReminderFeed(
            thisWeek = thisWeek,
            upcoming = upcoming,
            suggestions = suggestions(customs),
        )
    }

    /**
     * One row per chased document type, for the paper with the latest expiry — last
     * year's policy and this year's can coexist, and the later one is the one covering
     * the owner. Lapsed papers yield no row (the vault's business), and types with no
     * lead days (RC, loan, other) are never chased.
     */
    private fun documentRows(
        documents: List<Document>,
        today: LocalDate,
    ): List<UpcomingReminder.DocumentRenewal> = documents
        .filter { it.expiresOn != null && DocumentReminderPolicy.leadDaysFor(it.type).isNotEmpty() }
        .groupBy { it.type }
        .mapNotNull { (type, ofType) ->
            val kind = ReminderKind.forDocument(type) ?: return@mapNotNull null
            val current = ofType.maxByOrNull { it.expiresOn!! } ?: return@mapNotNull null
            val expiresOn = current.expiresOn!!

            val urgency = when (val validity = current.validity(today)) {
                is DocumentValidity.Expired, DocumentValidity.NoExpiry -> return@mapNotNull null
                is DocumentValidity.ExpiringSoon ->
                    if (validity.daysLeft <= THIS_WEEK_DAYS) {
                        ReminderUrgency.DUE_THIS_WEEK
                    } else {
                        ReminderUrgency.DUE_SOON
                    }
                is DocumentValidity.Valid -> ReminderUrgency.ON_TRACK
            }

            UpcomingReminder.DocumentRenewal(
                kind = kind,
                documentId = current.id,
                type = type,
                expiresOn = expiresOn,
                daysLeft = today.daysUntil(expiresOn),
                urgency = urgency,
                currentNudgeOn = DocumentReminderPolicy.scheduleFor(type, expiresOn, today)
                    .firstOrNull()?.on,
            )
        }

    /**
     * The service row. A never-serviced car yields none: there is no interval to be
     * inside of, and Home's attention card is what prompts the first log.
     *
     * The kind names the half of the rule that is binding — time or distance — because
     * that is what the server's `reminder_type` distinguishes and what the copy says
     * ("service due in 800 km" vs "last service was 5 months ago").
     */
    private fun serviceRow(
        status: ServiceDueStatus,
        today: LocalDate,
    ): UpcomingReminder.ServiceDue? = when (status) {
        ServiceDueStatus.NeverServiced -> null

        is ServiceDueStatus.NotDue -> UpcomingReminder.ServiceDue(
            kind = ReminderKind.SERVICE_DUE_TIME,
            status = status,
            dueOn = today.plus(status.daysLeft, DateTimeUnit.DAY),
            urgency = ReminderUrgency.ON_TRACK,
        )

        is ServiceDueStatus.DueSoon -> UpcomingReminder.ServiceDue(
            kind = if (status.daysLeft <= ServiceIntervalPolicy.DUE_SOON_DAYS) {
                ReminderKind.SERVICE_DUE_TIME
            } else {
                ReminderKind.SERVICE_DUE_KM
            },
            status = status,
            dueOn = today.plus(status.daysLeft, DateTimeUnit.DAY),
            urgency = if (status.daysLeft <= THIS_WEEK_DAYS) {
                ReminderUrgency.DUE_THIS_WEEK
            } else {
                ReminderUrgency.DUE_SOON
            },
        )

        is ServiceDueStatus.Overdue -> UpcomingReminder.ServiceDue(
            kind = if (status.daysOverdue > 0) {
                ReminderKind.SERVICE_DUE_TIME
            } else {
                ReminderKind.SERVICE_DUE_KM
            },
            status = status,
            dueOn = today.minus(status.daysOverdue, DateTimeUnit.DAY),
            urgency = ReminderUrgency.DUE_THIS_WEEK,
        )
    }

    /**
     * One row per live custom reminder. A dismissed occurrence is looked *past* — the
     * row shows the occurrence behind it rather than disappearing outright — so
     * "not this fortnight" still leaves next fortnight's check on the list.
     */
    private fun customRows(
        customs: List<CustomReminder>,
        dismissed: Set<ReminderDismissal>,
        today: LocalDate,
        currentKm: Int?,
    ): List<UpcomingReminder.Custom> = customs.mapNotNull { reminder ->
        var occurrence = CadencePlanner.nextOccurrence(reminder, from = today)
            ?: return@mapNotNull null

        val date = (occurrence as? ReminderOccurrence.OnDate)?.date
        if (date != null && ReminderDismissal(ReminderKind.CUSTOM, date, reminder.id) in dismissed) {
            occurrence = CadencePlanner.nextOccurrence(
                reminder,
                from = date.plus(1, DateTimeUnit.DAY),
            ) ?: return@mapNotNull null
        }

        UpcomingReminder.Custom(
            reminder = reminder,
            occurrence = occurrence,
            urgency = customUrgency(occurrence, today, currentKm),
        )
    }

    private fun customUrgency(
        occurrence: ReminderOccurrence,
        today: LocalDate,
        currentKm: Int?,
    ): ReminderUrgency = when (occurrence) {
        is ReminderOccurrence.OnDate -> {
            val daysLeft = today.daysUntil(occurrence.date)
            when {
                daysLeft <= THIS_WEEK_DAYS -> ReminderUrgency.DUE_THIS_WEEK
                daysLeft <= CUSTOM_DUE_SOON_DAYS -> ReminderUrgency.DUE_SOON
                else -> ReminderUrgency.ON_TRACK
            }
        }

        is ReminderOccurrence.AtOdometer -> {
            val kmLeft = currentKm?.let { occurrence.targetKm - it }
            when {
                kmLeft == null -> ReminderUrgency.ON_TRACK
                kmLeft <= 0 -> ReminderUrgency.DUE_THIS_WEEK
                kmLeft <= DISTANCE_DUE_SOON_KM -> ReminderUrgency.DUE_SOON
                else -> ReminderUrgency.ON_TRACK
            }
        }
    }

    /**
     * The presets with no reminder behind them. A paused reminder still counts as set
     * up — the owner made a decision about that topic, and suggesting it again would
     * argue with them.
     */
    private fun suggestions(customs: List<CustomReminder>): List<ReminderPreset> {
        val taken = customs.mapNotNull { it.preset }.toSet()
        return ReminderPreset.entries.filterNot { it in taken }
    }

    /** Orders rows inside a bucket: dated ones by date, distance targets last. */
    private fun UpcomingReminder.sortKey(today: LocalDate): Int = when (this) {
        is UpcomingReminder.DocumentRenewal -> daysLeft
        is UpcomingReminder.ServiceDue -> today.daysUntil(dueOn)
        is UpcomingReminder.Custom -> when (val occ = occurrence) {
            is ReminderOccurrence.OnDate -> today.daysUntil(occ.date)
            is ReminderOccurrence.AtOdometer -> Int.MAX_VALUE
        }
    }
}
