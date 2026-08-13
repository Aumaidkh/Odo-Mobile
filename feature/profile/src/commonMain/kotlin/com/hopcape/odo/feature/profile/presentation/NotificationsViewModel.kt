package com.hopcape.odo.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.settings.model.NotificationPreferences
import com.hopcape.odo.core.domain.settings.model.NotificationSchedule
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.feature.profile.domain.usecase.UpdateSettingsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the notifications screen.
 *
 * Every switch writes immediately — there is no Save button on this screen, so a toggle
 * that only lived in memory would be lost on the way back. The state follows what is
 * stored, so a failed write shows the switch back where it was.
 */
internal class NotificationsViewModel(
    settings: AppSettingsRepository,
    private val updateSettings: UpdateSettingsUseCase,
    private val telemetry: ProfileTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        telemetry.settingsOpened(ProfileTelemetry.Screen.NOTIFICATIONS)
        viewModelScope.launch {
            settings.observe()
                .catch { cause -> telemetry.readFailed(ProfileTelemetry.Screen.NOTIFICATIONS, cause) }
                .collect { stored ->
                    _state.update {
                        it.copy(
                            preferences = stored.notifications,
                            schedule = stored.notificationSchedule,
                        )
                    }
                }
        }
    }

    fun onEvent(event: NotificationsEvent) {
        when (event) {
            is NotificationsEvent.DocumentLeadToggled -> saveSchedule(
                _state.value.schedule.withLeadDays(
                    type = event.type,
                    days = _state.value.schedule.leadDaysFor(event.type).let { current ->
                        if (event.selected) current + event.days else current - event.days
                    },
                ),
            )

            is NotificationsEvent.NotifyHourChosen ->
                saveSchedule(_state.value.schedule.copy(notifyAtHour = event.hour))

            else -> saveTopics(event)
        }
    }

    /** The switches, which each replace one flag on what is stored. */
    private fun saveTopics(event: NotificationsEvent) {
        val current = _state.value.preferences
        val updated = when (event) {
            is NotificationsEvent.DocumentExpiryToggled -> current.copy(documentExpiry = event.enabled)
            is NotificationsEvent.ServiceDueToggled -> current.copy(serviceDue = event.enabled)
            is NotificationsEvent.CustomRemindersToggled -> current.copy(customReminders = event.enabled)
            is NotificationsEvent.OverchargeAlertsToggled -> current.copy(overchargeAlerts = event.enabled)
            is NotificationsEvent.MonthlySummaryToggled -> current.copy(monthlySummary = event.enabled)
            is NotificationsEvent.HealthScoreDropsToggled -> current.copy(healthScoreDrops = event.enabled)
            is NotificationsEvent.PushToggled -> current.copy(push = event.enabled)
            // Handled in onEvent; a schedule change is not a topic.
            is NotificationsEvent.DocumentLeadToggled, is NotificationsEvent.NotifyHourChosen -> return
        }
        save(updated)
    }

    /**
     * Store the schedule. Same immediate write as the switches, and the same reason: there
     * is no Save button on this screen, and the schedulers rebuild from what is stored.
     */
    private fun saveSchedule(schedule: NotificationSchedule) {
        _state.update { it.copy(error = null) }
        viewModelScope.launch(telemetry.op(ProfileTelemetry.Trace.SAVE_SETTING)) {
            val value = "${schedule.notifyAtHour}h"
            telemetry.settingSave(ProfileTelemetry.Setting.NOTIFICATIONS, value) {
                updateSettings.notificationSchedule(schedule)
            }.onLeft { error ->
                _state.update { it.copy(error = error.toProfileMessage()) }
            }
        }
    }

    private fun save(preferences: NotificationPreferences) {
        _state.update { it.copy(error = null) }
        viewModelScope.launch(telemetry.op(ProfileTelemetry.Trace.SAVE_SETTING)) {
            val value = "${preferences.enabledTopics}"
            telemetry.settingSave(ProfileTelemetry.Setting.NOTIFICATIONS, value) {
                updateSettings.notifications(preferences)
            }.onLeft { error ->
                _state.update { it.copy(error = error.toProfileMessage()) }
            }
        }
    }
}
