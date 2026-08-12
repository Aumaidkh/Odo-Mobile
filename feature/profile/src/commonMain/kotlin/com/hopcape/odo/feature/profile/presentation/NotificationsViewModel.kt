package com.hopcape.odo.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.settings.model.NotificationPreferences
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
                .collect { stored -> _state.update { it.copy(preferences = stored.notifications) } }
        }
    }

    fun onEvent(event: NotificationsEvent) {
        val current = _state.value.preferences
        val updated = when (event) {
            is NotificationsEvent.DocumentExpiryToggled -> current.copy(documentExpiry = event.enabled)
            is NotificationsEvent.ServiceDueToggled -> current.copy(serviceDue = event.enabled)
            is NotificationsEvent.CustomRemindersToggled -> current.copy(customReminders = event.enabled)
            is NotificationsEvent.OverchargeAlertsToggled -> current.copy(overchargeAlerts = event.enabled)
            is NotificationsEvent.MonthlySummaryToggled -> current.copy(monthlySummary = event.enabled)
            is NotificationsEvent.HealthScoreDropsToggled -> current.copy(healthScoreDrops = event.enabled)
            is NotificationsEvent.PushToggled -> current.copy(push = event.enabled)
        }
        save(updated)
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
