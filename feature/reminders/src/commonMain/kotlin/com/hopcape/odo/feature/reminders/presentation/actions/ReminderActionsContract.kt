package com.hopcape.odo.feature.reminders.presentation.actions

/**
 * The sheet's identity, parsed from the navigation key's primitives by the route host.
 * A typed bundle rather than three positional Koin parameters, so a nullable string can
 * never be matched to the wrong slot.
 */
internal data class ReminderActionsArgs(
    val kind: String,
    val dueOn: String?,
    val customId: String?,
)

/** What the owner did on the actions sheet, as data. */
internal sealed interface ReminderActionsEvent {

    /** "Snooze" — dismiss this occurrence; the next one stays on the list. */
    data object SnoozeTapped : ReminderActionsEvent

    /** "Turn off" — pause a custom reminder, or flip a derived kind's topic off. */
    data object TurnOffTapped : ReminderActionsEvent

    /** "Reschedule" — open the custom reminder's edit form. Custom rows only. */
    data object RescheduleTapped : ReminderActionsEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface ReminderActionsEffect {

    data object Close : ReminderActionsEffect

    /** Open the New screen in edit mode for this custom reminder. */
    data class OpenEdit(val reminderId: String) : ReminderActionsEffect
}
