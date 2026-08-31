package com.hopcape.odo.feature.reminders.presentation

import com.hopcape.odo.core.domain.reminder.model.ReminderPreset

/**
 * What the owner did on the reminders home, as data.
 *
 * [ReminderTapped] and [SuggestionTapped] carry the row's *resolved* copy alongside the
 * data: the sheet echoes the card it came from, and a one-tap create stores the
 * suggestion's name — both need the string, and only the composition can resolve a
 * resource, so the screen resolves it at the tap.
 */
internal sealed interface RemindersEvent {

    /** A this-week card was tapped. Opens its actions sheet. */
    data class ReminderTapped(val row: ReminderRow, val title: String, val due: String) : RemindersEvent

    /** "Remind me" on a suggestion — creates the preset's reminder in place. */
    data class SuggestionTapped(val preset: ReminderPreset, val name: String) : RemindersEvent

    /**
     * The suggestion's row itself (not its "Remind me" button) — opens the create form
     * pre-filled with the preset's defaults instead of creating blind. Both remain: this
     * is for someone who wants to adjust something before it's a real reminder.
     */
    data class SuggestionRowTapped(val preset: ReminderPreset, val name: String) : RemindersEvent

    data object ManageTapped : RemindersEvent

    data object AddTapped : RemindersEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface RemindersEffect {

    /** Open the actions sheet, carrying identity + the card's echo as primitives. */
    data class OpenActions(
        val kind: String,
        val dueOn: String?,
        val customId: String?,
        val title: String,
        val due: String,
        val icon: String,
    ) : RemindersEffect

    data object OpenSettings : RemindersEffect

    data object OpenNew : RemindersEffect

    /** Open the create form pre-filled with a suggestion's preset, per [RemindersEvent.SuggestionRowTapped]. */
    data class OpenNewFromSuggestion(val presetName: String, val name: String) : RemindersEffect
}
