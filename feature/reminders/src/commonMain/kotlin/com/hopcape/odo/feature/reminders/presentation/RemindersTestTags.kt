package com.hopcape.odo.feature.reminders.presentation

/**
 * Test tags for the parts of the reminders screens that copy alone cannot target — a
 * settings row's switch has no text of its own, and "Service due" appears both as a list
 * row and a settings topic.
 *
 * Public, like every feature's test-tags object: the end-to-end suite lives in
 * `:androidApp` and drives the real screens.
 */
object RemindersTestTags {

    const val ADD_FAB = "reminders_add_fab"

    /** One suggestion row's "Remind me" button; [presetName] is the enum constant's name. */
    fun remindMe(presetName: String) = "reminders_remind_me_$presetName"

    /** The switch on one settings row; [name] is the [ReminderToggle] constant's name. */
    fun settingsToggle(name: String) = "reminders_settings_toggle_$name"

    /** The disabled WhatsApp switch, which has no toggle constant on purpose. */
    const val SETTINGS_WHATSAPP_SWITCH = "reminders_settings_toggle_WHATSAPP"
}
