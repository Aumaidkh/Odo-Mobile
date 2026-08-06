package com.hopcape.odo.feature.reminders.presentation.create

import com.hopcape.odo.core.domain.reminder.model.ReminderPreset

/**
 * The form's mode, from the navigation key: `null` creates, an id edits. A typed bundle
 * rather than a raw nullable Koin parameter, so absence cannot be mistaken for a value.
 */
internal data class NewReminderArgs(val reminderId: String?)

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

    data object ChangeChannelsTapped : NewReminderEvent

    data object SaveTapped : NewReminderEvent

    data object CloseTapped : NewReminderEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface NewReminderEffect {

    data object OpenSettings : NewReminderEffect

    data object Close : NewReminderEffect
}
