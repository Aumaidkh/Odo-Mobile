package com.hopcape.odo.feature.reminders.presentation.create

import com.hopcape.odo.core.domain.reminder.model.ReminderPreset

/**
 * The form's mode, from the navigation key: `null` creates, an id edits. A typed bundle
 * rather than a raw nullable Koin parameter, so absence cannot be mistaken for a value.
 *
 * [suggestedPreset] + [suggestedName] pre-fill a create with that preset's defaults —
 * tapping a suggestion row rather than its "Remind me" button. Ignored when [reminderId]
 * is set; an edit's own stored data always wins.
 */
internal data class NewReminderArgs(
    val reminderId: String?,
    val suggestedPreset: String? = null,
    val suggestedName: String? = null,
)

/** What the owner did on the create/edit form, as data. */
internal sealed interface NewReminderEvent {

    /** A ready-made topic chip. [defaultName] is its resolved title, prefilled as the name. */
    data class PresetSelected(val preset: ReminderPreset, val defaultName: String) : NewReminderEvent

    /** The owner typed their own topic into the "+ Custom" chip. */
    data class CustomLabelSaved(val label: String) : NewReminderEvent

    data class NameChanged(val name: String) : NewReminderEvent

    data class RepeatChanged(val repeat: ReminderRepeat) : NewReminderEvent

    data class StartChanged(val millis: Long) : NewReminderEvent

    data class TimeChanged(val hour: Int, val minute: Int) : NewReminderEvent

    /**
     * The by-distance step, already converted to kilometres — the screen reads the owner's
     * typed number in their own unit and converts it before sending this, the same way it
     * converts an odometer entry.
     */
    data class DistanceStepChanged(val km: Int) : NewReminderEvent

    data object ChangeChannelsTapped : NewReminderEvent

    data object SaveTapped : NewReminderEvent

    data object CloseTapped : NewReminderEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface NewReminderEffect {

    data object OpenSettings : NewReminderEffect

    data object Close : NewReminderEffect
}
