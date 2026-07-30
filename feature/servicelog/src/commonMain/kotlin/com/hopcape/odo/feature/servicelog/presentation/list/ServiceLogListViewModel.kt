package com.hopcape.odo.feature.servicelog.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.feature.servicelog.domain.usecase.ObserveServiceLogFeedUseCase
import com.hopcape.odo.feature.servicelog.presentation.ServiceLogTelemetry
import com.hopcape.odo.feature.servicelog.presentation.list.model.ServiceLogDirection
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_error_load_failed
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the service-log list. Holds [ServiceLogListUiState], consumes
 * [ServiceLogListEvent]s, and emits one-shot [ServiceLogListEffect]s.
 *
 * It orchestrates and nothing more: the rows, the header stats and the chip counts all come
 * from one read ([ObserveServiceLogFeedUseCase]), turning that feed into rows is
 * [toContent]'s job, and where a tap goes is the route host's. What is left here — and all
 * that should be — is which car is being observed, how the owner asked to see it, and which
 * taps leave the screen.
 *
 * The two view choices are flows of their own rather than fields written into the state, so
 * a re-read of the car's entries can never drop them: the state *is* the combination of what
 * the car has and how the owner asked to see it.
 */
internal class ServiceLogListViewModel(
    carId: CarId,
    observeFeed: ObserveServiceLogFeedUseCase,
    private val telemetry: ServiceLogTelemetry,
) : ViewModel() {

    private val filter = MutableStateFlow(ServiceLogFilter.ALL)
    private val direction = MutableStateFlow(ServiceLogDirection.LEDGER)

    private val _effects = Channel<ServiceLogListEffect>(Channel.BUFFERED)
    val effects: Flow<ServiceLogListEffect> = _effects.receiveAsFlow()

    /**
     * What the car has, seen the way the owner asked for it.
     *
     * A failed read becomes [ServiceLogListUiState.Content.Failed] rather than an empty
     * list: the local DB is this app's source of truth, so a read that fails means the
     * record is unreadable — and telling an owner with six services that they have none is
     * the worse of the two lies.
     */
    val state: StateFlow<ServiceLogListUiState> =
        combine(observeFeed(carId), filter, direction) { feed, filter, direction ->
            ServiceLogListUiState(content = feed.toContent(filter), filter = filter, direction = direction)
        }
            .catch { cause ->
                telemetry.readFailed(ServiceLogTelemetry.Source.LIST, cause)
                emit(ServiceLogListUiState(content = loadFailed(), filter = filter.value, direction = direction.value))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = ServiceLogListUiState(),
            )

    init {
        telemetry.listOpened()
    }

    fun onEvent(event: ServiceLogListEvent) = when (event) {
        is ServiceLogListEvent.View -> onViewEvent(event)
        is ServiceLogListEvent.Open -> onOpenEvent(event)
    }

    /** How to look at the list — answered entirely in state, nothing leaves the screen. */
    private fun onViewEvent(event: ServiceLogListEvent.View) = when (event) {
        is ServiceLogListEvent.View.FilterSelected -> selectFilter(event.filter)
        is ServiceLogListEvent.View.DirectionSelected -> selectDirection(event.direction)
    }

    /** Somewhere to go — each becomes the effect the route host performs. */
    private fun onOpenEvent(event: ServiceLogListEvent.Open) = when (event) {
        is ServiceLogListEvent.Open.Entry -> emit(ServiceLogListEffect.OpenEntry(event.id))
        ServiceLogListEvent.Open.AddForm -> emit(ServiceLogListEffect.OpenAddForm)
        ServiceLogListEvent.Open.BillScanner -> {
            telemetry.scanBillClicked(ServiceLogTelemetry.Source.LIST)
            emit(ServiceLogListEffect.OpenBillScanner)
        }

        ServiceLogListEvent.Open.ShareRecord -> emit(ServiceLogListEffect.OpenShareRecord)
        ServiceLogListEvent.Open.Filters -> emit(ServiceLogListEffect.OpenFilters)
        ServiceLogListEvent.Open.Back -> emit(ServiceLogListEffect.NavigateBack)
    }

    private fun selectFilter(selected: ServiceLogFilter) {
        telemetry.filterSelected(selected)
        filter.update { selected }
    }

    private fun selectDirection(selected: ServiceLogDirection) {
        telemetry.directionSelected(selected)
        direction.update { selected }
    }

    private fun loadFailed(): ServiceLogListUiState.Content =
        ServiceLogListUiState.Content.Failed(UiText(Res.string.sl_error_load_failed))

    private fun emit(effect: ServiceLogListEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        /**
         * How long the feed keeps flowing after the last collector leaves — long enough to
         * survive a configuration change, or a trip into an entry and back, without
         * re-reading the whole log on the way.
         */
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
