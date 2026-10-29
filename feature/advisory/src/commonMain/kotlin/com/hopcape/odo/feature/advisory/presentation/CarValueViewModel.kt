package com.hopcape.odo.feature.advisory.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.feature.advisory.domain.CarValued
import com.hopcape.odo.feature.advisory.domain.LoadMakeLogoUseCase
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
    firstRun: Boolean = false,
    firstRunSteps: Int = 4,
    autoOdometerEnabled: Boolean = false,
    private val loadMakeLogo: LoadMakeLogoUseCase? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        CarValueUiState(
            firstRun = firstRun,
            offerAutoOdometer = firstRun && autoOdometerEnabled,
            firstRunSteps = firstRunSteps,
        ),
    )
    val state: StateFlow<CarValueUiState> = _state.asStateFlow()

    private val _effects = Channel<CarValueEffect>(Channel.BUFFERED)
    val effects: Flow<CarValueEffect> = _effects.receiveAsFlow()

    /** Whether the screen has already reported itself as shown. */
    private var reported = false

    /** The make whose logo was last looked up. */
    private var logoMake: String? = null

    init {
        observe()
    }

    /** The card's first appearance is counted once per screen. */
    private var autoOdoCardSeen = false

    /** The end of first run is counted once, by whichever exit the owner took first. */
    private var firstRunFinished = false

    fun onEvent(event: CarValueEvent) = when (event) {
        CarValueEvent.ScanClicked -> {
            telemetry.scanClicked(firstRun = _state.value.firstRun)
            finishFirstRun(AdvisoryTelemetry.Via.SCAN)
            emit(CarValueEffect.OpenScanner(firstRun = _state.value.firstRun))
        }

        is CarValueEvent.ShareClicked -> {
            telemetry.shareClicked()
            emit(CarValueEffect.Share(event.text))
        }

        CarValueEvent.AddOdometerClicked -> emit(CarValueEffect.OpenUpdateOdometer)

        CarValueEvent.AddCarClicked -> emit(CarValueEffect.OpenAddCar)

        // In first run the car is already saved, so back ends first run like Skip does.
        CarValueEvent.BackClicked -> if (_state.value.firstRun) {
            telemetry.firstRunSkipped(viaBack = true)
            finishFirstRun(AdvisoryTelemetry.Via.BACK)
            emit(CarValueEffect.FinishFirstRun)
        } else {
            emit(CarValueEffect.NavigateBack)
        }

        CarValueEvent.TurnOnAutoOdometerClicked -> {
            telemetry.autoOdoOfferAccepted()
            finishFirstRun(AdvisoryTelemetry.Via.AUTO_ODOMETER)
            emit(CarValueEffect.OpenAutoOdometer)
        }

        CarValueEvent.AutoOdometerCardSeen -> {
            if (!autoOdoCardSeen) telemetry.autoOdoCardSeen()
            autoOdoCardSeen = true
        }

        is CarValueEvent.WhyRangeToggled -> telemetry.whyRangeToggled(event.expanded)

        CarValueEvent.SkipClicked -> {
            telemetry.firstRunSkipped(viaBack = false)
            finishFirstRun(AdvisoryTelemetry.Via.SKIP)
            emit(CarValueEffect.FinishFirstRun)
        }
    }

    private fun finishFirstRun(via: String) {
        if (!_state.value.firstRun || firstRunFinished) return
        firstRunFinished = true
        telemetry.firstRunFinished(via)
    }

    private fun observe() {
        viewModelScope.launch(telemetry.op(AdvisoryTelemetry.Trace.LOAD)) {
            telemetry.timeToFirstValue(observeCarValue()).collect { snapshot ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        valued = snapshot.valued,
                        odometerAssumed = snapshot.odometerAssumed,
                    )
                }
                report(snapshot.valued)
                snapshot.valued?.car?.make?.let(::resolveLogo)
            }
        }
    }

    /** Looks the logo up once per make, since the estimate re-emits on every log change. */
    private fun resolveLogo(make: String) {
        if (make == logoMake) return
        logoMake = make
        val load = loadMakeLogo ?: return
        viewModelScope.launch {
            val url = load(make)
            _state.update { it.copy(makeLogoUrl = url) }
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
        telemetry.valueShown(hasRecord = !valued.value.hasNoRecord, firstRun = _state.value.firstRun)
        if (_state.value.offerAutoOdometer) telemetry.autoOdoOffered()
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
