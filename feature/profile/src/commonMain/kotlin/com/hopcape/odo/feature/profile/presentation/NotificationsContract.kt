package com.hopcape.odo.feature.profile.presentation

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.settings.model.NotificationPreferences

/** Which switch the owner moved on the notifications screen. */
internal sealed interface NotificationsEvent {

    data class DocumentExpiryToggled(val enabled: Boolean) : NotificationsEvent
    data class ServiceDueToggled(val enabled: Boolean) : NotificationsEvent
    data class CustomRemindersToggled(val enabled: Boolean) : NotificationsEvent
    data class OverchargeAlertsToggled(val enabled: Boolean) : NotificationsEvent
    data class MonthlySummaryToggled(val enabled: Boolean) : NotificationsEvent
    data class HealthScoreDropsToggled(val enabled: Boolean) : NotificationsEvent
    data class PushToggled(val enabled: Boolean) : NotificationsEvent
}

/**
 * Display state for the notifications screen.
 *
 * [preferences] is the domain type rather than seven booleans copied out of it: the screen
 * needs exactly what it holds, and a copy would be one more place to forget a new topic.
 *
 * [error] is set when a write fails. The switches keep showing what is *stored*, so a
 * failed toggle springs back rather than lying about being on.
 */
@Immutable
internal data class NotificationsUiState(
    val preferences: NotificationPreferences = NotificationPreferences(),
    val error: UiText? = null,
)
