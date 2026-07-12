package com.hopcape.odo.feature.reminders.presentation.settings

/** How far ahead of a due date Odo nudges the owner. */
internal enum class RemindBefore { D3, D7, D14, D30 }

/** Every toggle on the settings screen — the channels and the topics. */
internal enum class ReminderToggle { PUSH, WHATSAPP, EMAIL, INSURANCE, SERVICE, TYRE, PARTNER }

/**
 * Display state for the reminder settings — the notification channels, the topics to be
 * reminded about, and the lead time. The reminder engine reads these; the screen just
 * flips them.
 */
internal data class ReminderSettingsUiState(
    val push: Boolean,
    val whatsapp: Boolean,
    val email: Boolean,
    val insurance: Boolean,
    val service: Boolean,
    val tyre: Boolean,
    val partner: Boolean,
    val remindBefore: RemindBefore,
) {
    fun isOn(toggle: ReminderToggle): Boolean = when (toggle) {
        ReminderToggle.PUSH -> push
        ReminderToggle.WHATSAPP -> whatsapp
        ReminderToggle.EMAIL -> email
        ReminderToggle.INSURANCE -> insurance
        ReminderToggle.SERVICE -> service
        ReminderToggle.TYRE -> tyre
        ReminderToggle.PARTNER -> partner
    }

    fun toggled(toggle: ReminderToggle): ReminderSettingsUiState = when (toggle) {
        ReminderToggle.PUSH -> copy(push = !push)
        ReminderToggle.WHATSAPP -> copy(whatsapp = !whatsapp)
        ReminderToggle.EMAIL -> copy(email = !email)
        ReminderToggle.INSURANCE -> copy(insurance = !insurance)
        ReminderToggle.SERVICE -> copy(service = !service)
        ReminderToggle.TYRE -> copy(tyre = !tyre)
        ReminderToggle.PARTNER -> copy(partner = !partner)
    }
}

/** Sample settings (mirrors the mockup: email + partner off, 7-day lead time). */
internal fun sampleReminderSettings(): ReminderSettingsUiState = ReminderSettingsUiState(
    push = true,
    whatsapp = true,
    email = false,
    insurance = true,
    service = true,
    tyre = true,
    partner = false,
    remindBefore = RemindBefore.D7,
)
