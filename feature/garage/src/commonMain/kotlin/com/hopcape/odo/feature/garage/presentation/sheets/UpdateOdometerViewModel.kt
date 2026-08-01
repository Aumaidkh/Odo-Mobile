package com.hopcape.odo.feature.garage.presentation.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.feature.garage.domain.usecase.GetOdometerContextUseCase
import com.hopcape.odo.feature.garage.domain.usecase.UpdateOdometerUseCase
import com.hopcape.odo.feature.garage.presentation.GarageTelemetry
import com.hopcape.odo.feature.garage.presentation.state.Loadable
import com.hopcape.odo.feature.garage.presentation.state.Submission
import com.hopcape.odo.feature.garage.presentation.toOdometerMessage
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_error_no_car
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the update-odometer sheet.
 *
 * A refused reading leaves the sheet open with the reason on it. The owner has a number in
 * front of them that the car's history contradicts, and closing the sheet would throw away
 * both the number and the explanation.
 */
internal class UpdateOdometerViewModel(
    private val activeCar: ActiveCarProvider,
    private val getContext: GetOdometerContextUseCase,
    private val updateOdometer: UpdateOdometerUseCase,
    private val telemetry: GarageTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateOdometerUiState())
    val state: StateFlow<UpdateOdometerUiState> = _state.asStateFlow()

    private val _effects = Channel<UpdateOdometerEffect>(Channel.BUFFERED)
    val effects: Flow<UpdateOdometerEffect> = _effects.receiveAsFlow()

    init {
        telemetry.odometerSheetOpened()
        load()
    }

    fun onEvent(event: UpdateOdometerEvent) = when (event) {
        is UpdateOdometerEvent.Save -> save(event.km)
    }

    private fun load() {
        val carId = activeCar.activeCarId.value
        if (carId == null) {
            telemetry.noActiveCar(GarageTelemetry.Screen.ODOMETER)
            _state.update { it.copy(context = Loadable.Failed(UiText(Res.string.gr_error_no_car))) }
            return
        }
        viewModelScope.launch {
            getContext(carId).fold(
                ifLeft = { error ->
                    _state.update { it.copy(context = Loadable.Failed(error.toOdometerMessage())) }
                },
                ifRight = { context -> _state.update { it.copy(context = Loadable.Ready(context)) } },
            )
        }
    }

    private fun save(km: Long) {
        val carId = activeCar.activeCarId.value
        if (carId == null) {
            telemetry.noActiveCar(GarageTelemetry.Screen.ODOMETER)
            _state.update { it.copy(submission = Submission.Failed(UiText(Res.string.gr_error_no_car))) }
            return
        }

        _state.update { it.copy(submission = Submission.InFlight) }
        viewModelScope.launch(telemetry.op(GarageTelemetry.Trace.SAVE_ODOMETER)) {
            telemetry.odometerSave { updateOdometer(carId, km.toInt()) }.fold(
                ifLeft = { error ->
                    _state.update { it.copy(submission = Submission.Failed(error.toOdometerMessage())) }
                },
                ifRight = {
                    _state.update { it.copy(submission = Submission.Succeeded) }
                    _effects.trySend(UpdateOdometerEffect.Saved)
                },
            )
        }
    }
}
