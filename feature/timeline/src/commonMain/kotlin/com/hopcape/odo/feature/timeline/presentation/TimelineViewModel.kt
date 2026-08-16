package com.hopcape.odo.feature.timeline.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.Plan
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.showcase.ShowcaseArbiter
import com.hopcape.odo.core.domain.showcase.ShowcaseHookId
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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
    private val showcase: ShowcaseArbiter,
    entitlements: EntitlementSource,
    private val telemetry: TimelineTelemetry,
) : ViewModel() {

    /** True while the record coach mark holds the arbiter's grant (#233). */
    private val recordShowcaseVisible = MutableStateFlow(false)

    /** One ask per visit — reset when the surface is left, so the next visit may ask again. */
    private var recordShowcaseRequested = false

    private val proPlan = entitlements.observe().map { it.plan == Plan.PRO }.catch { emit(false) }

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
                flowOf(TimelineUiState(noCar = true))
            } else {
                combine(observeTimeline(id), filters.filter) { snapshot, filter ->
                    TimelineUiState(content = Loadable.Ready(snapshot.toContent(filter)))
                }
            }
        }
        .combine(recordShowcaseVisible) { ui, visible -> ui.copy(recordShowcase = visible) }
        // Read only to pick the Pro-gated coach mark's copy — never to hide it.
        .combine(proPlan) { ui, pro -> ui.copy(proPlan = pro) }
        .onEach(::maybeRequestRecordShowcase)
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

        TimelineEvent.RecordShowcaseDismissed -> {
            recordShowcaseVisible.value = false
            viewModelScope.launch { showcase.dismissed(ShowcaseHookId.RECORD_EXPORT) }
            Unit
        }

        TimelineEvent.RecordShowcaseActedOn -> {
            recordShowcaseVisible.value = false
            viewModelScope.launch { showcase.actedOn(ShowcaseHookId.RECORD_EXPORT) }
            carId?.let { send(TimelineEffect.ShareRecord(carId = it.value)) } ?: Unit
        }

        // Not seen: the owner never answered — a navigation did. The hook keeps its one
        // showing, and the reset lets the next visit ask again.
        TimelineEvent.RecordShowcaseLeft -> {
            if (recordShowcaseVisible.value) showcase.surfaceLeft(ShowcaseHookId.RECORD_EXPORT)
            recordShowcaseVisible.value = false
            recordShowcaseRequested = false
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
    /**
     * The record hook's due-condition (#233): three or more verified services on the feed
     * — by then the owner has invested enough that the export is worth showing off, and
     * the PDF has enough rows to argue for itself.
     */
    private suspend fun maybeRequestRecordShowcase(ui: TimelineUiState) {
        if (recordShowcaseRequested) return
        val content = ui.content.valueOrNull ?: return
        val verifiedServices = content.events.count {
            it is ActivityEvent.Service && it.verification == VerificationStatus.VERIFIED
        }
        if (verifiedServices < RECORD_SHOWCASE_MIN_VERIFIED) return
        recordShowcaseRequested = true
        if (showcase.request(ShowcaseHookId.RECORD_EXPORT)) {
            recordShowcaseVisible.value = true
        }
    }

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

        /** #233: fewer rows than this argues against the export rather than for it. */
        const val RECORD_SHOWCASE_MIN_VERIFIED = 3
    }
}

/** Domain snapshot + the chosen filter to display state. Decided here, once. */
private fun TimelineSnapshot.toContent(filter: TimelineFilter): TimelineContent = TimelineContent(
    carName = carName,
    events = filter.apply(events),
    totalEvents = events.size,
    isFiltered = !filter.hidesNothing,
)
