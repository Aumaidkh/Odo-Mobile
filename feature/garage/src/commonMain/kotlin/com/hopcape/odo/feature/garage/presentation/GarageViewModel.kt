package com.hopcape.odo.feature.garage.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.feature.garage.domain.model.AutoOdometerCardState
import com.hopcape.odo.feature.garage.domain.model.GarageDocument
import com.hopcape.odo.feature.garage.domain.model.verifiedCount
import com.hopcape.odo.feature.garage.domain.usecase.GarageSnapshot
import com.hopcape.odo.feature.garage.domain.usecase.ObserveAutoOdometerCardState
import com.hopcape.odo.feature.garage.domain.usecase.ObserveGarageUseCase
import com.hopcape.odo.feature.garage.presentation.GarageEffect.*
import com.hopcape.odo.feature.garage.presentation.state.Loadable
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_error_load_failed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

/**
 * State holder for the garage's home base.
 *
 * The car comes from [ActiveCarProvider] rather than a navigation key: the garage is a tab,
 * reached without naming a car, and every per-car surface answering "which car?" for itself
 * is how the app ends up opening someone else's.
 */
internal class GarageViewModel(
    private val activeCar: ActiveCarProvider,
    observeGarage: ObserveGarageUseCase,
    observeAutoOdometerCard: ObserveAutoOdometerCardState,
    private val telemetry: GarageTelemetry,
) : ViewModel() {

    private val _effects = Channel<GarageEffect>(Channel.BUFFERED)
    val effects: Flow<GarageEffect> = _effects.receiveAsFlow()

    private val filter = MutableStateFlow(GarageUiState().filter)

    /** Guards the opened event so a re-emission does not count a second visit. */
    private var reportedOpen = false

    /** Guards the auto-odometer card's shown event the same way — once per visit. */
    private var reportedAutoOdometerCardShown = false

    /**
     * The car, its documents and its history, resolved for today.
     *
     * A failed read becomes [Loadable.Failed] rather than an empty garage. The local DB is
     * the source of truth, so a read that fails means the garage is unreadable, and telling
     * an owner with four services that they have none is the worse of the two lies.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<GarageUiState> = activeCar.activeCarId
        .flatMapLatest { carId ->
            // No car yet means setup has not finished — the "add your car" state, which is
            // a real answer rather than a wait.
            if (carId == null) {
                flowOf(emptyContent())
            } else {
                combine(observeGarage(carId), observeAutoOdometerCard(carId), ::toContent)
            }
        }
        .onEach(::reportOpened)
        .catch { cause ->
            telemetry.readFailed(GarageTelemetry.Screen.HOME, cause)
            emit(Loadable.Failed(UiText(Res.string.gr_error_load_failed)))
        }
        .combine(filter) { content, facet -> GarageUiState(content = content, filter = facet) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = GarageUiState(),
        )

    fun onEvent(event: GarageEvent) = when (event) {
        is GarageEvent.FilterSelected -> selectFilter(event)
        GarageEvent.AddCarTapped -> emit(GarageEffect.OpenAddCar)
        GarageEvent.UpdateOdometerTapped -> emit(GarageEffect.OpenUpdateOdometer)
        GarageEvent.CarMenuTapped -> emit(GarageEffect.OpenCarActions)
        GarageEvent.ManageDocumentsTapped -> emit(GarageEffect.OpenDocumentVault)
        is GarageEvent.DocumentTapped -> emit(OpenDocument(event.id))
        GarageEvent.AddDocumentTapped -> emit(GarageEffect.OpenAddDocument)
        GarageEvent.AddServiceTapped -> emit(GarageEffect.OpenAddToHistory)
        is GarageEvent.ServiceTapped -> openService(event)
        GarageEvent.AutoOdometerCardTapped -> {
            telemetry.autoOdometerCardTapped()
            emit(GarageEffect.OpenAutoOdometerEducation)
        }
        GarageEvent.AutoOdometerStatusTileTapped -> emit(GarageEffect.OpenAutoOdometerSettings)
        GarageEvent.ViewAllChallans -> emit(GarageEffect.OpenChallans)
    }

    private fun selectFilter(event: GarageEvent.FilterSelected) {
        telemetry.historyFiltered(event.facet.name)
        filter.value = event.facet
    }

    /**
     * The service log's own screens are per-car, so the key needs the car. Without one there
     * is nothing to open, and opening the wrong car's record is worse than not opening one.
     */
    private fun openService(event: GarageEvent.ServiceTapped) {
        val carId = activeCar.activeCarId.value
        if (carId == null) {
            telemetry.noActiveCar(GarageTelemetry.Screen.HOME)
            return
        }
        emit(GarageEffect.OpenService(logId = event.id, carId = carId.value))
    }

    private fun emit(effect: GarageEffect) {
        _effects.trySend(effect)
        Unit
    }

    /** The numbers the garage exists to move, reported once per visit. */
    private fun reportOpened(content: Loadable<GarageContent>) {
        val value = (content as? Loadable.Ready)?.value ?: return
        reportAutoOdometerCardShown(value.autoOdometerCard)

        if (reportedOpen) return
        reportedOpen = true

        if (value.car == null) {
            telemetry.garageEmpty()
            return
        }
        telemetry.garageOpened(
            services = value.history.size,
            verified = value.history.verifiedCount(),
            documentsMissing = value.documents.count { it is GarageDocument.Missing },
            documentsExpiring = value.documents.count { it is GarageDocument.OnFile && it.needsAttention },
        )
    }

    /** The pitch card's north-star signal, reported once per visit the card actually shows. */
    private fun reportAutoOdometerCardShown(cardState: AutoOdometerCardState) {
        if (reportedAutoOdometerCardShown) return
        if (cardState !is AutoOdometerCardState.NotSetUp) return
        reportedAutoOdometerCardShown = true
        telemetry.autoOdometerCardShown()
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/** Nothing set up yet — a loaded garage with no car, not a wait. */
private fun emptyContent(): Loadable<GarageContent> = Loadable.Ready(
    GarageContent(car = null, documents = emptyList(), history = emptyList()),
)

private fun toContent(
    snapshot: GarageSnapshot,
    autoOdometerCard: AutoOdometerCardState,
): Loadable<GarageContent> = Loadable.Ready(
    GarageContent(
        car = snapshot.car,
        odometer = snapshot.currentOdometer,
        documents = snapshot.documents,
        history = snapshot.history,
        autoOdometerCard = autoOdometerCard,
    ),
)
