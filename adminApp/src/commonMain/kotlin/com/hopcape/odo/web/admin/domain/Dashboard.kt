package com.hopcape.odo.web.admin.domain

import arrow.core.Either
import com.hopcape.odo.web.core.domain.WebError

/**
 * One day's signups, for the fourteen-day bar chart.
 *
 * Days with no signups are present with `count = 0` rather than absent. A series
 * built only from the days that have rows silently rescales the axis and the quiet
 * day vanishes instead of reading as zero.
 */
data class SignupDay(val date: String, val count: Int) {
    /** `2026-09-01` → `01`. The axis has fourteen labels and no room for more. */
    val dayLabel: String get() = date.takeLast(2)
}

/** One line of the recent-activity list. */
data class ActivityEntry(
    val action: String,
    val subjectType: String,
    val actorEmail: String?,
    val at: String,
)

/**
 * Something waiting on a person.
 *
 * [count] is what the row is about and [route] is where it is dealt with, so the
 * dashboard can be a set of links rather than a set of numbers somebody then has to
 * go and find. Wording lives in the panel's string table, keyed by [kind].
 */
data class AttentionItem(val kind: Kind, val count: Int) {
    enum class Kind { UrgentTickets, OpenTickets, VehicleSubmissions, CitySubmissions, DraftPosts, PastDueSubscriptions }
}

/**
 * Everything the dashboard draws, from one `admin_dashboard()` call.
 *
 * Counts only. The sentences around them are composed in the panel, because they
 * have to be translatable and a string table does not belong in SQL.
 */
data class DashboardSnapshot(
    val users: Int,
    val users7d: Int,
    val usersPrev7d: Int,
    val cars: Int,
    val serviceLogs: Int,
    val documents: Int,
    val subsActive: Int,
    val subsPastDue: Int,
    val ticketsOpen: Int,
    val ticketsUrgent: Int,
    val postsPublished: Int,
    val postsDraft: Int,
    val vehiclePending: Int,
    val cityPending: Int,
    val signups: List<SignupDay>,
    val activity: List<ActivityEntry>,
) {
    /**
     * Week-on-week change in signups, as a percentage, or null when last week was
     * zero.
     *
     * Null rather than 100%: everything is an infinite increase on nothing, and a
     * dashboard that says "+100%" over a week with one signup is worse than one
     * that says nothing.
     */
    val signupDelta: Int?
        get() = if (usersPrev7d == 0) null else ((users7d - usersPrev7d) * 100) / usersPrev7d

    /**
     * What needs a person, worst first, with the empty rows dropped.
     *
     * Order is deliberate: money and angry people before housekeeping.
     */
    val attention: List<AttentionItem>
        get() = listOf(
            AttentionItem(AttentionItem.Kind.UrgentTickets, ticketsUrgent),
            AttentionItem(AttentionItem.Kind.PastDueSubscriptions, subsPastDue),
            AttentionItem(AttentionItem.Kind.OpenTickets, ticketsOpen - ticketsUrgent),
            AttentionItem(AttentionItem.Kind.VehicleSubmissions, vehiclePending),
            AttentionItem(AttentionItem.Kind.CitySubmissions, cityPending),
            AttentionItem(AttentionItem.Kind.DraftPosts, postsDraft),
        ).filter { it.count > 0 }
}

interface DashboardRepository {
    suspend fun snapshot(): Either<WebError, DashboardSnapshot>
}
