package com.hopcape.odo.feature.timeline.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.feature.timeline.domain.model.TimelineFilter
import com.hopcape.odo.feature.timeline.domain.usecase.ObserveTimelineUseCase
import com.hopcape.odo.feature.timeline.domain.usecase.TimelineSnapshot
import com.hopcape.odo.feature.timeline.presentation.state.Loadable
import com.hopcape.odo.feature.timeline.presentation.state.valueOrNull
import com.hopcape.odo.feature.timeline.resources.Res
import com.hopcape.odo.feature.timeline.resources.tl_error_load_failed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
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
 * State holder for the Timeline tab. Holds [TimelineUiState], consumes [TimelineEvent]s, and
 * emits [TimelineEffect]s for the route host to navigate on.
 *
 * The car comes from [ActiveCarProvider] rather than a navigation key: this is a bottom tab,
 * reached without naming a car, and every per-car surface answering "which car?" for itself
 * is how the app ends up showing someone else's.
 *
 * The filter is combined in here rather than applied by the composable, so the header's
 * "showing N of M" and the rows underneath it are decided in one place and cannot disagree.
 */
internal class TimelineViewModel(
    activeCar: ActiveCarProvider,
    observeTimeline: ObserveTimelineUseCase,
    private val filters: TimelineFilterStore,
    private val telemetry: TimelineTelemetry,
) : ViewModel() {

    private val _effects = Channel<TimelineEffect>(Channel.BUFFERED)
    val effects: Flow<TimelineEffect> = _effects.receiveAsFlow()

    /** The car the feed is of, held for the per-car destinations the header opens. */
    private var carId: CarId? = null

    /** Guards the opened event so a re-read does not count a second visit. */
    private var reportedOpen = false

    /**
     * The car's feed.
     *
     * A failed read becomes [Loadable.Failed] rather than an empty feed: the local DB is the
     * source of truth, so a read that fails means the history is unknown, and a car with
     * five years of bills must not be told its story starts here because a query broke.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TimelineUiState> = activeCar.activeCarId
        .onEach { carId = it }
        .flatMapLatest { id ->
            // No car yet means setup has not finished. There is no history to show, and
            // nothing truthful to say about a car that does not exist.
            if (id == null) {
                flowOf(TimelineUiState())
            } else {
                combine(observeTimeline(id), filters.filter) { snapshot, filter ->
                    TimelineUiState(content = Loadable.Ready(snapshot.toContent(filter)))
                }
            }
        }
        .onEach(::reportOpened)
        .catch { cause ->
            telemetry.readFailed(cause)
            emit(TimelineUiState(content = Loadable.Failed(UiText(Res.string.tl_error_load_failed))))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = TimelineUiState(),
        )

    fun onEvent(event: TimelineEvent) = when (event) {
        TimelineEvent.FilterTapped -> {
            telemetry.filterOpened()
            send(TimelineEffect.OpenFilter)
        }

        TimelineEvent.ShareTapped -> {
            telemetry.shareTapped()
            carId?.let { send(TimelineEffect.ShareRecord(carId = it.value)) } ?: Unit
        }

        is TimelineEvent.ServiceTapped -> {
            telemetry.serviceOpened()
            carId?.let { send(TimelineEffect.OpenService(logId = event.id.value, carId = it.value)) } ?: Unit
        }

        is TimelineEvent.AddBillTapped -> {
            telemetry.addBillTapped()
            send(TimelineEffect.OpenScanner)
        }

        TimelineEvent.ScanFirstTapped -> {
            telemetry.scanFirstTapped()
            send(TimelineEffect.OpenScanner)
        }
    }

    /**
     * The record the tab opened on, reported once per visit. An empty feed and a five-year
     * history are different product problems, so the count and whether anything has been
     * serviced both ride along.
     */
    private fun reportOpened(state: TimelineUiState) {
        val content = state.content.valueOrNull ?: return
        if (reportedOpen) return
        reportedOpen = true
        telemetry.timelineOpened(
            eventCount = content.totalEvents,
            hasServices = content.events.any { it is ActivityEvent.Service },
            isNewUser = content.isNewUser,
        )
    }

    private fun send(effect: TimelineEffect) {
        _effects.trySend(effect)
        Unit
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/** Domain snapshot + the chosen filter to display state. Decided here, once. */
private fun TimelineSnapshot.toContent(filter: TimelineFilter): TimelineContent = TimelineContent(
    carName = carName,
    events = filter.apply(events),
    totalEvents = events.size,
    isFiltered = !filter.hidesNothing,
)
