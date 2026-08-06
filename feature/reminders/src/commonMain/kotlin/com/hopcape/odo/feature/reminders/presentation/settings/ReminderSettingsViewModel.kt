package com.hopcape.odo.feature.reminders.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.settings.model.NotificationPreferences
import com.hopcape.odo.feature.reminders.domain.usecase.ObserveReminderSettingsUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.UpdateReminderSettingsUseCase
import com.hopcape.odo.feature.reminders.presentation.RemindersTelemetry
import com.hopcape.odo.feature.reminders.presentation.state.Loadable
import com.hopcape.odo.feature.reminders.presentation.state.valueOrNull
import com.hopcape.odo.feature.reminders.resources.Res
import com.hopcape.odo.feature.reminders.resources.rm_error_load_failed
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State holder for the reminder settings. The screen edits the same
 * [NotificationPreferences] store the profile's notifications screen does — one store,
 * two doors.
 *
 * A toggle writes the store and lets the store's flow move the switch: the local DB
 * answers fast enough that optimism would only buy a state that can lie when the write
 * fails.
 */
internal class ReminderSettingsViewModel(
    observeSettings: ObserveReminderSettingsUseCase,
    private val updateSettings: UpdateReminderSettingsUseCase,
    private val telemetry: RemindersTelemetry,
) : ViewModel() {

    private val _effects = Channel<ReminderSettingsEffect>(Channel.BUFFERED)
    val effects: Flow<ReminderSettingsEffect> = _effects.receiveAsFlow()

    private val preferences = observeSettings()

    val state: StateFlow<ReminderSettingsUiState> = preferences
        .map { prefs -> ReminderSettingsUiState(Loadable.Ready(prefs.toContent())) }
        .catch { cause ->
            telemetry.readFailed(RemindersTelemetry.Screen.SETTINGS, cause)
            emit(ReminderSettingsUiState(Loadable.Failed(UiText(Res.string.rm_error_load_failed))))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = ReminderSettingsUiState(),
        )

    fun onEvent(event: ReminderSettingsEvent) {
        when (event) {
            is ReminderSettingsEvent.Toggled -> toggle(event.toggle)
            ReminderSettingsEvent.BackTapped -> _effects.trySend(ReminderSettingsEffect.NavigateBack)
        }
    }

    private fun toggle(toggle: ReminderToggle) {
        val enabled = state.value.content.valueOrNull?.isOn(toggle)?.not() ?: return
        viewModelScope.launch(telemetry.op(OP_TOGGLE)) {
            val current = preferences.first()
            telemetry.settingsToggle(field = toggle.name, enabled = enabled) {
                updateSettings(current.withToggle(toggle, enabled))
            }
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        const val OP_TOGGLE = "settingsToggle"
    }
}

private fun NotificationPreferences.toContent() = ReminderSettingsContent(
    push = push,
    whatsapp = whatsapp,
    documents = documentExpiry,
    service = serviceDue,
    custom = customReminders,
    partner = partnerOffers,
)

private fun NotificationPreferences.withToggle(
    toggle: ReminderToggle,
    enabled: Boolean,
): NotificationPreferences = when (toggle) {
    ReminderToggle.PUSH -> copy(push = enabled)
    ReminderToggle.DOCUMENTS -> copy(documentExpiry = enabled)
    ReminderToggle.SERVICE -> copy(serviceDue = enabled)
    ReminderToggle.CUSTOM -> copy(customReminders = enabled)
    ReminderToggle.PARTNER -> copy(partnerOffers = enabled)
}
