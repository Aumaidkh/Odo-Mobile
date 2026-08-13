package com.hopcape.odo.feature.profile.presentation

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.settings.model.NotificationPreferences
import com.hopcape.odo.core.domain.settings.model.NotificationSchedule

/** Which switch the owner moved on the notifications screen. */
internal sealed interface NotificationsEvent {

    data class DocumentExpiryToggled(val enabled: Boolean) : NotificationsEvent
    data class ServiceDueToggled(val enabled: Boolean) : NotificationsEvent
    data class CustomRemindersToggled(val enabled: Boolean) : NotificationsEvent
    data class OverchargeAlertsToggled(val enabled: Boolean) : NotificationsEvent
    data class MonthlySummaryToggled(val enabled: Boolean) : NotificationsEvent
    data class HealthScoreDropsToggled(val enabled: Boolean) : NotificationsEvent
    data class PushToggled(val enabled: Boolean) : NotificationsEvent

    /**
     * One lead chip on one kind of paper was tapped. [days] is the chip; the screen sends
     * what it wants the state to become rather than a toggle, so a stale render cannot flip
     * the wrong way.
     */
    data class DocumentLeadToggled(
        val type: DocumentType,
        val days: Int,
        val selected: Boolean,
    ) : NotificationsEvent

    /** The hour of the day every reminder should arrive at. */
    data class NotifyHourChosen(val hour: Int) : NotificationsEvent
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
    /** When they arrive: the lead days per kind of paper, and the hour of day. */
    val schedule: NotificationSchedule = NotificationSchedule(),
    val error: UiText? = null,
)
