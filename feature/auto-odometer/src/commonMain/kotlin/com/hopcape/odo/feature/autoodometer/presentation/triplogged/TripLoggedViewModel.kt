package com.hopcape.odo.feature.autoodometer.presentation.triplogged

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.DistanceArg
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.servicelog.policy.ServiceDueStatus
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.trip.model.Trip
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripStatus
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.triptracker.TrackingPreconditions
import com.hopcape.odo.feature.autoodometer.domain.model.DerivedOdometer
import com.hopcape.odo.feature.autoodometer.domain.usecase.AcknowledgeTrip
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObserveDerivedOdometer
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObserveServiceDueNudge
import com.hopcape.odo.feature.autoodometer.domain.usecase.RejectTrip
import com.hopcape.odo.feature.autoodometer.presentation.AutoOdometerTelemetry
import com.hopcape.odo.feature.autoodometer.resources.Res
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_duration_hours_minutes
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_duration_minutes
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_nudge_due_days
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_nudge_due_km
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_nudge_overdue_days
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_nudge_overdue_km
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * State holder for the trip-logged screen (M6) — the odometer drum, the trip's stats, the
 * optional service-due nudge, and the plan §5 step-5 background-location upgrade card.
 *
 * Loads the target [Trip] straight off [TripRepository] (the same pattern
 * [ObserveDerivedOdometer]/[RejectTrip] already use, rather than a wrapper use case for one
 * port call) alongside [ObserveDerivedOdometer] (the drum values) and
 * [ObserveServiceDueNudge] (the nudge). All three are keyed on the active car, resolved once
 * — this feature is single-car for the MVP (same footing as [ActiveCarProvider]'s other
 * one-shot readers, [com.hopcape.odo.feature.autoodometer.presentation.devicepicker.DevicePickerViewModel]
 * included).
 *
 * The reject reflow needs no extra plumbing: [TripRepository.observe] is the same flow this
 * class and [ObserveDerivedOdometer] both collect, so [RejectTrip] landing re-emits it and
 * the drum's new (lower) value falls out of the existing `combine`.
 *
 * [AutoOdometerTelemetry.firstTripLogged] (the funnel's terminal event) fires on "Done" when
 * [AppSettings.aoLastAckedTripEndedAt][com.hopcape.odo.core.domain.settings.model.AppSettings.aoLastAckedTripEndedAt]
 * is still null right before [acknowledgeTrip] advances it — no trip has ever been
 * acknowledged on this device, so this is the first one. Cheaper than a new schema column.
 */
internal class TripLoggedViewModel(
    private val tripId: TripId,
    private val trips: TripRepository,
    private val observeDerivedOdometer: ObserveDerivedOdometer,
    private val observeServiceDueNudge: ObserveServiceDueNudge,
    private val acknowledgeTrip: AcknowledgeTrip,
    private val rejectTrip: RejectTrip,
    private val activeCar: ActiveCarProvider,
    private val preconditions: TrackingPreconditions,
    private val settings: AppSettingsRepository,
    private val telemetry: AutoOdometerTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(
        TripLoggedUiState(
            backgroundUpgrade = BackgroundUpgradeCardState(granted = preconditions.status().backgroundLocation),
        ),
    )
    val state: StateFlow<TripLoggedUiState> = _state.asStateFlow()

    private val _effects = Channel<TripLoggedEffect>(Channel.BUFFERED)
    val effects: Flow<TripLoggedEffect> = _effects.receiveAsFlow()

    /** The loaded trip's `endedAt` — what [AcknowledgeTrip] needs on "Done". */
    private var tripEndedAt: Instant? = null

    /** The loaded trip's status — [doneTapped] needs it to know whether "Done" must confirm. */
    private var tripStatus: TripStatus? = null

    init {
        telemetry.tripLoggedViewed()
        val carId = activeCar.activeCarId.value
        if (carId == null) {
            telemetry.noActiveCar()
            _state.update { it.copy(loading = false) }
        } else {
            viewModelScope.launch(telemetry.op(TRACE_LOAD)) {
                combine(
                    trips.observe(carId),
                    observeDerivedOdometer(carId, tripId),
                    observeServiceDueNudge(carId),
                ) { allTrips, derived, dueStatus ->
                    Triple(allTrips.find { it.id == tripId }, derived, dueStatus)
                }.collect { (trip, derived, dueStatus) -> applyLoadedTrip(trip, derived, dueStatus) }
            }
        }
    }

    /** Remembers the trip's endedAt/status for [doneTapped], then updates everything the screen shows. */
    private fun applyLoadedTrip(trip: Trip?, derived: DerivedOdometer?, dueStatus: ServiceDueStatus) {
        tripEndedAt = trip?.endedAt
        tripStatus = trip?.status
        _state.update {
            it.copy(
                loading = false,
                odometerNowKm = derived?.current?.km,
                odometerBeforeKm = derived?.before?.km,
                addedKm = derived?.let { d -> d.current.km - d.before.km },
                duration = trip?.let { t -> (t.endedAt - t.startedAt).inWholeMinutes.toDurationText() },
                nudge = dueStatus.toNudgeText(),
            )
        }
    }

    fun onEvent(event: TripLoggedEvent) {
        when (event) {
            TripLoggedEvent.DoneTapped -> doneTapped()
            TripLoggedEvent.RejectTapped -> _state.update { it.copy(showRejectConfirm = true) }
            TripLoggedEvent.RejectDismissed -> _state.update { it.copy(showRejectConfirm = false) }
            TripLoggedEvent.RejectConfirmed -> rejectConfirmed()
            TripLoggedEvent.UpgradeTapped -> _state.update {
                it.copy(backgroundUpgrade = it.backgroundUpgrade.copy(askedOnce = true))
            }
            TripLoggedEvent.UpgradeDismissed -> _state.update {
                it.copy(backgroundUpgrade = it.backgroundUpgrade.copy(dismissed = true))
            }
            is TripLoggedEvent.BackgroundLocationStatusObserved -> backgroundLocationStatusObserved(event.status)
        }
    }

    private fun doneTapped() {
        val endedAt = tripEndedAt ?: return
        _state.update { it.copy(acknowledging = true) }
        viewModelScope.launch(telemetry.op(TRACE_ACKNOWLEDGE)) {
            val isFirstEver = isFirstTripEverLogged()
            confirmTripIfPending()
            acknowledgeTrip(endedAt)
            if (isFirstEver) telemetry.firstTripLogged()
            _state.update { it.copy(acknowledging = false) }
            send(TripLoggedEffect.NavigateBack)
        }
    }

    /**
     * A null marker means no trip has ever been acknowledged on this device — read before
     * [AcknowledgeTrip] advances it, which is the cheapest correct "is this the first one"
     * signal without a new schema column (plan §4.3/§7 F10).
     */
    private suspend fun isFirstTripEverLogged(): Boolean =
        settings.observe().first().aoLastAckedTripEndedAt == null

    /**
     * "Done" is the owner's implicit confirmation — "That wasn't me driving" is the only
     * path that disagrees. Without this, a trip the validation ladder flagged
     * NEEDS_CONFIRMATION never becomes CONFIRMED, so its distance never counts
     * (TripStatus.counted) even after the owner has looked at it and moved on.
     */
    private suspend fun confirmTripIfPending() {
        if (tripStatus == TripStatus.NEEDS_CONFIRMATION) {
            trips.setStatus(tripId, TripStatus.CONFIRMED)
        }
    }

    private fun rejectConfirmed() {
        _state.update { it.copy(showRejectConfirm = false) }
        viewModelScope.launch(telemetry.op(TRACE_REJECT)) {
            rejectTrip(tripId)
            telemetry.tripRejected()
        }
    }

    private fun backgroundLocationStatusObserved(status: PermissionStatus) {
        val current = _state.value.backgroundUpgrade
        val granted = status == PermissionStatus.Granted
        if (granted == current.granted) return

        // Only an explicit tap on the card counts as "the ask was answered" — a passive
        // re-read (this screen's own first composition, or an unrelated permission change
        // caught by the same controller) must not fire the funnel event.
        if (current.askedOnce) telemetry.backgroundUpgradeAnswered(granted)
        _state.update { it.copy(backgroundUpgrade = it.backgroundUpgrade.copy(granted = granted)) }
    }

    private fun send(effect: TripLoggedEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    /**
     * "Oil change in about 1,800 km" (plan §4.1) — the trip-logged screen's own copy for
     * whichever [ServiceDueStatus] the car is in. `null` (hides the section) for
     * [ServiceDueStatus.NeverServiced] and [ServiceDueStatus.NotDue] — a car with nothing
     * due soon does not earn a nudge here.
     */
    private fun ServiceDueStatus.toNudgeText(): UiText? = when (this) {
        is ServiceDueStatus.DueSoon -> if (kmLeft != null) {
            UiText(Res.string.ao_trip_logged_nudge_due_km, listOf(DistanceArg(kmLeft!!)))
        } else {
            UiText(Res.string.ao_trip_logged_nudge_due_days, listOf(daysLeft))
        }

        is ServiceDueStatus.Overdue -> if (kmOverdue != null) {
            UiText(Res.string.ao_trip_logged_nudge_overdue_km, listOf(DistanceArg(kmOverdue!!)))
        } else {
            UiText(Res.string.ao_trip_logged_nudge_overdue_days, listOf(daysOverdue))
        }

        ServiceDueStatus.NeverServiced, is ServiceDueStatus.NotDue -> null
    }

    /** "24 min" under an hour, "1h 04m" at or past it — the Duration stat row's copy. */
    private fun Long.toDurationText(): UiText = if (this < MINUTES_PER_HOUR) {
        UiText(Res.string.ao_trip_logged_duration_minutes, listOf(this))
    } else {
        UiText(Res.string.ao_trip_logged_duration_hours_minutes, listOf(this / MINUTES_PER_HOUR, this % MINUTES_PER_HOUR))
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60L
        const val TRACE_LOAD = "load_trip"
        const val TRACE_ACKNOWLEDGE = "acknowledge_trip"
        const val TRACE_REJECT = "reject_trip"
    }
}
