package com.hopcape.odo.feature.autoodometer.presentation.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.feature.autoodometer.presentation.AutoOdometerTelemetry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the education screen (M2). The screen is static copy for [mode], so
 * there is no use case to call — this ViewModel exists only to record the funnel's first
 * step and turn the two taps into navigation (docs/AUTO_ODOMETER_PLAN.md §7 F4).
 *
 * [mode] is a route argument (Koin `parametersOf`), mapped from the nav-local
 * `AutoOdometerFlowMode` at the route host — the domain-shaped [TriggerMode] is what this
 * feature's use cases already speak (see `EnrollTriggerDevice`), so the ViewModel layer
 * never needs the nav type.
 */
internal class EducationViewModel(
    mode: TriggerMode,
    private val telemetry: AutoOdometerTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(EducationUiState(mode = mode))
    val state: StateFlow<EducationUiState> = _state.asStateFlow()

    private val _effects = Channel<EducationEffect>(Channel.BUFFERED)
    val effects: Flow<EducationEffect> = _effects.receiveAsFlow()

    init {
        telemetry.educationViewed(mode.name)
    }

    fun onEvent(event: EducationEvent) {
        when (event) {
            EducationEvent.CtaTapped -> ctaTapped()
            is EducationEvent.NotificationStatusObserved ->
                _state.update { it.copy(notifications = event.status) }
            EducationEvent.CloseTapped -> send(EducationEffect.NavigateBack)
        }
    }

    /**
     * The CTA now leads to the notification step when there is a prompt left to raise, and
     * straight on when there is not.
     *
     * It used to fire the `POST_NOTIFICATIONS` request itself and navigate in the same breath,
     * which drew the system dialog on top of the next screen's rationale — the owner was asked
     * about notifications by a screen that had never mentioned them, over a page arguing for a
     * different permission entirely. The ask is a page of its own now, and this decides only
     * whether that page is worth showing.
     */
    private fun ctaTapped() {
        val state = _state.value
        if (state.shouldExplainNotifications) {
            send(EducationEffect.NavigateToNotificationRationale(state.mode))
            return
        }
        telemetry.notificationStepSkipped(state.notifications.name)
        send(onwardFor(state.mode))
    }

    /** Where the flow goes once notifications are settled — the branch this screen has always made. */
    private fun onwardFor(mode: TriggerMode): EducationEffect = when (mode) {
        TriggerMode.STEREO -> EducationEffect.NavigateToDevicePicker
        TriggerMode.NO_STEREO -> EducationEffect.NavigateToPermissionSetup(TriggerMode.NO_STEREO)
    }

    private fun send(effect: EducationEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
