package com.hopcape.odo.feature.advisory.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.feature.advisory.domain.CarValued
import com.hopcape.odo.feature.advisory.domain.CityTier
import com.hopcape.odo.feature.advisory.domain.ObserveCarValueUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for "my car's value".
 *
 * Collects rather than reads: the screen's whole argument is that scanning a bill moves the
 * number, so the number has to move while the owner is still on the screen after they come
 * back from the scanner.
 *
 * [AdvisoryTelemetry.valueShown] fires once per screen rather than per emission — the
 * estimate re-arrives whenever a log changes, and counting each of those would make the
 * funnel read as several visits.
 */
internal class CarValueViewModel(
    private val observeCarValue: ObserveCarValueUseCase,
    private val telemetry: AdvisoryTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(CarValueUiState())
    val state: StateFlow<CarValueUiState> = _state.asStateFlow()

    private val _effects = Channel<CarValueEffect>(Channel.BUFFERED)
    val effects: Flow<CarValueEffect> = _effects.receiveAsFlow()

    /** Whether the screen has already reported itself as shown. */
    private var reported = false

    init {
        observe()
    }

    fun onEvent(event: CarValueEvent) = when (event) {
        CarValueEvent.ScanClicked -> {
            telemetry.scanClicked()
            emit(CarValueEffect.OpenScanner)
        }

        is CarValueEvent.ShareClicked -> {
            telemetry.shareClicked()
            emit(CarValueEffect.Share(event.text))
        }

        CarValueEvent.BackClicked -> emit(CarValueEffect.NavigateBack)
    }

    private fun observe() {
        viewModelScope.launch(telemetry.op(AdvisoryTelemetry.Trace.LOAD)) {
            telemetry.timeToFirstValue(observeCarValue()).collect { valued ->
                _state.update { it.copy(isLoading = false, valued = valued) }
                report(valued)
            }
        }
    }

    /**
     * Report the screen once, with the fact that decides which owner is looking at it.
     *
     * The first emission is what the owner actually sees, so it is the one that counts —
     * and a car that never arrives is reported too, because a screen with nothing on it is
     * indistinguishable from a broken one otherwise.
     *
     * A city the estimate could not place is reported here rather than by the use case, so
     * it lands on this screen's trace and is said once rather than on every emission.
     */
    private fun report(valued: CarValued?) {
        if (reported) return
        reported = true
        if (valued == null) {
            telemetry.noCar()
            return
        }
        telemetry.valueShown(hasRecord = !valued.value.hasNoRecord)
        when (val city = valued.cityTier) {
            is CityTier.Unavailable -> telemetry.cityCatalogUnavailable(city.cause)
            is CityTier.NotListed -> telemetry.cityNotListed(city.catalogSize)
            CityTier.NotSet, is CityTier.Resolved -> Unit
        }
    }

    private fun emit(effect: CarValueEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
