package com.hopcape.odo.feature.reminders.presentation.settings

/** What the owner did on the settings screen, as data. */
internal sealed interface ReminderSettingsEvent {

    data class Toggled(val toggle: ReminderToggle) : ReminderSettingsEvent

    data object BackTapped : ReminderSettingsEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface ReminderSettingsEffect {

    data object NavigateBack : ReminderSettingsEffect
}
