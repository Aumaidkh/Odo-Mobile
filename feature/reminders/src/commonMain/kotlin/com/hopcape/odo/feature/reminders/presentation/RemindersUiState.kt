package com.hopcape.odo.feature.reminders.presentation

/** Which glyph (and tint) a reminder row shows, keyed off its kind. */
internal enum class ReminderIcon { SHIELD, OIL, LEAF, TYRE }

/** An upcoming reminder's state — a tinted pill, or a "remind me" opt-in for suggestions. */
internal sealed interface UpcomingStatus {
    /** Coming up within the window — amber pill. */
    data object DueSoon : UpcomingStatus
    /** Comfortably ahead — green pill. */
    data object OnTrack : UpcomingStatus
    /** Not tracked yet (a suggestion) — offers a "Remind me" opt-in. */
    data object Suggested : UpcomingStatus
}

/** A this-week item — urgent, tappable through to its detail (amber due stamp + chevron). */
internal data class ThisWeekItem(
    val icon: ReminderIcon,
    val title: String,
    val due: String,
)

/** An upcoming item — icon + title + subtitle + a trailing status. */
internal data class UpcomingItem(
    val icon: ReminderIcon,
    val title: String,
    val subtitle: String,
    val status: UpcomingStatus,
)

/** The summary header — attention (amber) or all-caught-up (green). */
internal sealed interface RemindersHeader {
    data class Attention(val count: Int, val detail: String) : RemindersHeader
    data object CaughtUp : RemindersHeader
}

/**
 * Display state for the Reminders screen. [thisWeek] is shown under its own heading only
 * when there's something urgent; [upcoming] is the always-present forward look.
 */
internal data class RemindersUiState(
    val header: RemindersHeader,
    val thisWeek: List<ThisWeekItem>,
    val upcoming: List<UpcomingItem>,
)

private val sampleUpcoming = listOf(
    UpcomingItem(ReminderIcon.OIL, "Oil change due", "At ~57,000 km · about 3 weeks", UpcomingStatus.DueSoon),
    UpcomingItem(ReminderIcon.LEAF, "PUC check", "Valid till 12 Nov · in 4 months", UpcomingStatus.OnTrack),
    UpcomingItem(ReminderIcon.TYRE, "Tyre rotation", "Suggested next month", UpcomingStatus.Suggested),
)

/** Sample with a urgent renewal this week + a service due soon (amber header). */
internal fun sampleRemindersAttention(): RemindersUiState = RemindersUiState(
    header = RemindersHeader.Attention(count = 2, detail = "1 renewal this week · 1 service due soon"),
    thisWeek = listOf(ThisWeekItem(ReminderIcon.SHIELD, "Insurance renewal", "Due in 7 days · 03 Jul")),
    upcoming = sampleUpcoming,
)

/** Sample with nothing urgent (green "all caught up" header, no this-week section). */
internal fun sampleRemindersCaughtUp(): RemindersUiState = RemindersUiState(
    header = RemindersHeader.CaughtUp,
    thisWeek = emptyList(),
    upcoming = sampleUpcoming,
)
