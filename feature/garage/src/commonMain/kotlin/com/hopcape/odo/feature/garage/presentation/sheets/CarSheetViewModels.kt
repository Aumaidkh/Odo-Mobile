package com.hopcape.odo.feature.garage.presentation.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.feature.garage.domain.usecase.GarageSnapshot
import com.hopcape.odo.feature.garage.domain.usecase.ObserveGarageUseCase
import com.hopcape.odo.feature.garage.domain.usecase.RemoveCarUseCase
import com.hopcape.odo.feature.garage.presentation.GarageTelemetry
import com.hopcape.odo.feature.garage.presentation.state.Loadable
import com.hopcape.odo.feature.garage.presentation.state.Submission
import com.hopcape.odo.feature.garage.presentation.state.valueOrNull
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_error_no_car
import com.hopcape.odo.feature.garage.resources.gr_error_remove_failed
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reads the car once for whichever sheet asked. A snapshot rather than a stream: a sheet is
 * open for a few seconds and is about the car as it stands, and re-reading under the
 * owner's finger is how a confirmation ends up describing something else.
 */
private suspend fun loadSummary(
    activeCar: ActiveCarProvider,
    observeGarage: ObserveGarageUseCase,
): Loadable<CarSummary> {
    val carId = activeCar.activeCarId.value ?: return Loadable.Failed(UiText(Res.string.gr_error_no_car))
    val snapshot = observeGarage(carId).first()
    return snapshot.toSummary() ?: Loadable.Failed(UiText(Res.string.gr_error_no_car))
}

private fun GarageSnapshot.toSummary(): Loadable.Ready<CarSummary>? {
    val car = car ?: return null
    return Loadable.Ready(
        CarSummary(
            displayName = car.displayName,
            registration = car.registrationNumber?.value,
            serviceCount = history.size,
            documentCount = documents.count { it is com.hopcape.odo.feature.garage.domain.model.GarageDocument.OnFile },
        ),
    )
}

/** State holder for the car-actions sheet — a header and three ways onward. */
internal class CarActionsViewModel(
    private val activeCar: ActiveCarProvider,
    private val observeGarage: ObserveGarageUseCase,
    private val telemetry: GarageTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(CarActionsUiState())
    val state: StateFlow<CarActionsUiState> = _state.asStateFlow()

    private val _effects = Channel<CarActionsEffect>(Channel.BUFFERED)
    val effects: Flow<CarActionsEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val car = loadSummary(activeCar, observeGarage)
            if (car is Loadable.Failed) telemetry.noActiveCar(GarageTelemetry.Screen.CAR_ACTIONS)
            _state.update { it.copy(car = car) }
        }
    }

    fun onEvent(event: CarActionsEvent) {
        val effect = when (event) {
            CarActionsEvent.EditTapped -> CarActionsEffect.OpenEdit
            CarActionsEvent.ExportTapped -> CarActionsEffect.OpenExport
            CarActionsEvent.RemoveTapped -> CarActionsEffect.OpenRemove
        }
        _effects.trySend(effect)
    }
}

/**
 * State holder for the remove-car confirmation.
 *
 * The counts on the sheet are what the owner is giving up, read before the tap rather than
 * after it — a confirmation that cannot say what it deletes is not a confirmation.
 */
internal class RemoveCarViewModel(
    private val activeCar: ActiveCarProvider,
    private val observeGarage: ObserveGarageUseCase,
    private val removeCar: RemoveCarUseCase,
    private val telemetry: GarageTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(RemoveCarUiState())
    val state: StateFlow<RemoveCarUiState> = _state.asStateFlow()

    private val _effects = Channel<RemoveCarEffect>(Channel.BUFFERED)
    val effects: Flow<RemoveCarEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val car = loadSummary(activeCar, observeGarage)
            if (car is Loadable.Failed) telemetry.noActiveCar(GarageTelemetry.Screen.REMOVE_CAR)
            _state.update { it.copy(car = car) }
        }
    }

    fun onEvent(event: RemoveCarEvent) = when (event) {
        RemoveCarEvent.ExportFirstTapped -> emit(RemoveCarEffect.OpenExport)
        RemoveCarEvent.CancelTapped -> emit(RemoveCarEffect.Dismiss)
        RemoveCarEvent.RemoveTapped -> remove()
    }

    private fun remove() {
        val carId = activeCar.activeCarId.value
        if (carId == null) {
            telemetry.noActiveCar(GarageTelemetry.Screen.REMOVE_CAR)
            _state.update { it.copy(submission = Submission.Failed(UiText(Res.string.gr_error_no_car))) }
            return
        }
        val summary = _state.value.car.valueOrNull

        _state.update { it.copy(submission = Submission.InFlight) }
        viewModelScope.launch(telemetry.op(GarageTelemetry.Trace.REMOVE_CAR)) {
            telemetry.carRemove(
                services = summary?.serviceCount ?: 0,
                documents = summary?.documentCount ?: 0,
            ) { removeCar(carId) }.fold(
                ifLeft = {
                    _state.update {
                        it.copy(submission = Submission.Failed(UiText(Res.string.gr_error_remove_failed)))
                    }
                },
                ifRight = {
                    _state.update { it.copy(submission = Submission.Succeeded) }
                    emit(RemoveCarEffect.Removed)
                },
            )
        }
    }

    private fun emit(effect: RemoveCarEffect) {
        _effects.trySend(effect)
        Unit
    }
}

/**
 * State holder for the export sheet. It shows what an export would contain and hands both
 * actions to the paywall — the record itself is the Resale Passport, which is paid and not
 * built yet.
 */
internal class ExportViewModel(
    private val activeCar: ActiveCarProvider,
    private val observeGarage: ObserveGarageUseCase,
    private val telemetry: GarageTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    private val _effects = Channel<ExportEffect>(Channel.BUFFERED)
    val effects: Flow<ExportEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val car = loadSummary(activeCar, observeGarage)
            if (car is Loadable.Failed) telemetry.noActiveCar(GarageTelemetry.Screen.EXPORT)
            _state.update { it.copy(car = car) }
        }
    }

    fun onEvent(event: ExportEvent) {
        val target = when (event) {
            ExportEvent.PdfTapped -> GarageTelemetry.ExportTarget.PDF
            ExportEvent.ShareTapped -> GarageTelemetry.ExportTarget.SHARE
        }
        telemetry.exportRequested(target)
        _effects.trySend(ExportEffect.OpenPaywall(PAYWALL_TRIGGER))
    }

    private companion object {
        /** Why the paywall was shown, so its copy can speak to the export the owner wanted. */
        const val PAYWALL_TRIGGER = "EXPORT"
    }
}
