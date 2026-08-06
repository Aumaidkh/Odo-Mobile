package com.hopcape.odo.feature.reminders.presentation.settings

import androidx.compose.runtime.Immutable
import com.hopcape.odo.feature.reminders.presentation.state.Loadable

/**
 * Every switch on the settings screen — the channels and the topics.
 *
 * WhatsApp has no switch constant on purpose: the row renders disabled ("coming soon")
 * until a sender exists, so no event can carry it. The Email channel and the global
 * "remind me before" lead time are gone entirely — Email was cut with the profile work,
 * and lead times are per-type policy (PRD §5.3), not a preference.
 */
internal enum class ReminderToggle { PUSH, DOCUMENTS, SERVICE, CUSTOM, PARTNER }

/**
 * The switches' values, projected from the device's one
 * [NotificationPreferences][com.hopcape.odo.core.domain.settings.model.NotificationPreferences]
 * store — the same one the profile's notifications screen edits.
 */
@Immutable
internal data class ReminderSettingsContent(
    val push: Boolean,
    val whatsapp: Boolean,
    val documents: Boolean,
    val service: Boolean,
    val custom: Boolean,
    val partner: Boolean,
) {
    fun isOn(toggle: ReminderToggle): Boolean = when (toggle) {
        ReminderToggle.PUSH -> push
        ReminderToggle.DOCUMENTS -> documents
        ReminderToggle.SERVICE -> service
        ReminderToggle.CUSTOM -> custom
        ReminderToggle.PARTNER -> partner
    }
}

/** Display state for the reminder settings screen. */
@Immutable
internal data class ReminderSettingsUiState(
    val content: Loadable<ReminderSettingsContent> = Loadable.Loading,
)

/** Sample settings for previews (Email is gone; WhatsApp renders disabled). */
internal fun sampleReminderSettings(): ReminderSettingsUiState = ReminderSettingsUiState(
    Loadable.Ready(
        ReminderSettingsContent(
            push = true,
            whatsapp = false,
            documents = true,
            service = true,
            custom = true,
            partner = false,
        ),
    ),
)
