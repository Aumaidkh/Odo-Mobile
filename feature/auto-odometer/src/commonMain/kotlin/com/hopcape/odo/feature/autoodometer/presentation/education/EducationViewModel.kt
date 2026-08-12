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
            EducationEvent.CloseTapped -> send(EducationEffect.NavigateBack)
        }
    }

    private fun ctaTapped() {
        val effect = when (_state.value.mode) {
            TriggerMode.STEREO -> EducationEffect.NavigateToDevicePicker
            TriggerMode.NO_STEREO -> EducationEffect.NavigateToPermissionSetup(TriggerMode.NO_STEREO)
        }
        send(effect)
    }

    private fun send(effect: EducationEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
