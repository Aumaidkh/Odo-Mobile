package com.hopcape.odo.feature.autoodometer.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.triptracker.TrackingReadiness
import com.hopcape.odo.core.triptracker.TrackingStatus
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.core.triptracker.TripTracker
import com.hopcape.odo.feature.autoodometer.domain.usecase.DeleteAllTripData
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObserveMonthlySummary
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObserveSetupState
import com.hopcape.odo.feature.autoodometer.domain.usecase.PauseTracking
import com.hopcape.odo.feature.autoodometer.domain.usecase.ResumeTracking
import com.hopcape.odo.feature.autoodometer.presentation.AutoOdometerTelemetry
import com.hopcape.odo.feature.autoodometer.resources.Res
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_trip_in_progress
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_trip_paused
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_waiting_generic
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_waiting_no_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_waiting_stereo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * State holder for the settings screen (M7): the tracking toggle, the trigger-device section,
 * the monthly stats card, the privacy actions, and the readiness fix-it rows.
 *
 * Readiness is not read from [ObserveSetupState] after the first load — that use case's
 * [com.hopcape.odo.feature.autoodometer.domain.model.SetupState.readiness] is a one-shot
 * snapshot (`TrackingPreconditions.status()` re-evaluated only when the reading count or the
 * enabled flag changes), which does not satisfy plan §5's "re-checked on every settings-screen
 * entry and app foreground" rule on its own. Instead the route host mounts one
 * [com.hopcape.odo.core.platform.permission.PermissionController] per relevant permission —
 * the same reactive plumbing [com.hopcape.odo.feature.autoodometer.presentation.permissions.PermissionSetupViewModel]
 * uses — and reports every change in via [SettingsEvent.ReadinessChanged]. That covers a fresh
 * screen entry (first composition) and a return from this screen's own settings deep-link
 * (the controller's settings-launcher callback) reactively; a permission revoked in the system
 * Settings app while this screen stays open and foregrounded without navigating away is not
 * caught until the next screen entry — the same limitation already accepted for every other
 * [com.hopcape.odo.core.platform.permission.PermissionController] use in this feature (F5, F6),
 * not a new gap this class introduces.
 */
internal class SettingsViewModel(
    private val tracker: TripTracker,
    private val observeSetupState: ObserveSetupState,
    private val observeMonthlySummary: ObserveMonthlySummary,
    private val pauseTracking: PauseTracking,
    private val resumeTracking: ResumeTracking,
    private val deleteAllTripData: DeleteAllTripData,
    private val settings: AppSettingsRepository,
    private val activeCar: ActiveCarProvider,
    private val clock: Clock,
    private val telemetry: AutoOdometerTelemetry,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = _effects.receiveAsFlow()

    /** The last readiness reading, kept outside [SettingsUiState] to diff a new one against. */
    private var lastReadiness: TrackingReadiness? = null

    init {
        val carId = activeCar.activeCarId.value
        if (carId == null) {
            telemetry.noActiveCar()
            _state.update { it.copy(loading = false) }
        } else {
            observeTracking(carId)
            loadMonthlySummary(carId)
        }
    }

    private fun observeTracking(carId: CarId) {
        viewModelScope.launch(telemetry.op(TRACE_OBSERVE)) {
            combine(
                tracker.isEnabled,
                tracker.status,
                observeSetupState(carId),
                settings.observe(),
            ) { enabled, status, setup, appSettings ->
                buildTrackingSnapshot(enabled, status, setup.bond?.triggerMode, setup.bond != null, appSettings.autoOdoPausedUntil)
            }.collect { snapshot ->
                // Merged onto the existing state (not a wholesale replacement) so this
                // collector never clobbers what loadMonthlySummary's own, independent
                // coroutine has already written into tripCount/monthlyDistanceKm.
                _state.update {
                    it.copy(
                        loading = false,
                        trackingEnabled = snapshot.enabled,
                        statusDetail = snapshot.statusDetail,
                        mode = snapshot.mode,
                        hasBond = snapshot.hasBond,
                        pausedUntil = snapshot.pausedUntil,
                        readinessIssues = issuesFor(snapshot.mode, lastReadiness),
                    )
                }
            }
        }
    }

    /** One combine emission's worth of tracking state, ahead of being merged into [SettingsUiState]. */
    private data class TrackingSnapshot(
        val enabled: Boolean,
        val statusDetail: UiText?,
        val mode: TriggerMode?,
        val hasBond: Boolean,
        val pausedUntil: LocalDate?,
    )

    private fun buildTrackingSnapshot(
        enabled: Boolean,
        status: TrackingStatus,
        mode: TriggerMode?,
        hasBond: Boolean,
        autoOdoPausedUntil: Instant?,
    ): TrackingSnapshot = TrackingSnapshot(
        enabled = enabled,
        statusDetail = status.toStatusDetail(mode),
        mode = mode,
        hasBond = hasBond,
        pausedUntil = autoOdoPausedUntil?.takeIf { it > clock.now() }?.toLocalDateTime(timeZone)?.date,
    )

    private fun loadMonthlySummary(carId: CarId) {
        viewModelScope.launch(telemetry.op(TRACE_MONTHLY)) {
            val summary = observeMonthlySummary(carId)
            _state.update { it.copy(tripCount = summary.tripCount, monthlyDistanceKm = summary.totalDistance.km) }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.ToggleTapped -> toggleTapped()
            SettingsEvent.ResumeTapped -> resumeTapped()
            SettingsEvent.ChangeDeviceTapped -> send(SettingsEffect.NavigateToDevicePicker)
            SettingsEvent.PauseWeekTapped -> pauseWeekTapped()
            SettingsEvent.DeleteTapped -> _state.update { it.copy(showDeleteConfirm = true) }
            SettingsEvent.DeleteConfirmed -> deleteConfirmed()
            SettingsEvent.DeleteDismissed -> _state.update { it.copy(showDeleteConfirm = false) }
            SettingsEvent.BackTapped -> send(SettingsEffect.NavigateBack)
            is SettingsEvent.ReadinessChanged -> readinessChanged(event.readiness)
        }
    }

    /** A direct mirror of `TripTracker.isEnabled` — not routed through Pause/ResumeTracking (plan §4.2). */
    private fun toggleTapped() {
        val next = !_state.value.trackingEnabled
        viewModelScope.launch(telemetry.op(TRACE_TOGGLE)) {
            tracker.setEnabled(next)
            telemetry.trackingToggled(next)
        }
    }

    private fun pauseWeekTapped() {
        viewModelScope.launch(telemetry.op(TRACE_PAUSE)) {
            pauseTracking()
            telemetry.pausedWeek()
        }
    }

    /**
     * Reuses [AutoOdometerTelemetry.trackingToggled] rather than a new event — resuming out of a
     * week's pause is, product-metric-wise, the same outcome as switching tracking back on.
     */
    private fun resumeTapped() {
        viewModelScope.launch(telemetry.op(TRACE_RESUME)) {
            resumeTracking()
            telemetry.trackingToggled(true)
        }
    }

    private fun deleteConfirmed() {
        _state.update { it.copy(showDeleteConfirm = false) }
        val carId = activeCar.activeCarId.value
        if (carId == null) {
            telemetry.noActiveCar()
            return
        }
        deleteTripDataFor(carId)
    }

    private fun deleteTripDataFor(carId: CarId) {
        _state.update { it.copy(deleting = true) }
        viewModelScope.launch(telemetry.op(TRACE_DELETE)) {
            deleteAllTripData(carId)
            telemetry.tripDataDeleted()
            _state.update { it.copy(deleting = false) }
        }
    }

    /**
     * Diffs [readiness] against [lastReadiness] for whichever [ReadinessIssue]s currently apply
     * (plan §5's `ao_precondition_lost` signal), then stores it as the new baseline.
     */
    private fun readinessChanged(readiness: TrackingReadiness) {
        val relevant = relevantReadinessIssues(_state.value.mode)
        reportNewlyLostPreconditions(relevant, readiness)
        lastReadiness = readiness
        _state.update { it.copy(readinessIssues = relevant.filterNot { issue -> issue.isGranted(readiness) }) }
    }

    /** Fires `ao_precondition_lost` for any [relevant] issue that was granted last time but isn't now. */
    private fun reportNewlyLostPreconditions(relevant: List<ReadinessIssue>, readiness: TrackingReadiness) {
        val previous = lastReadiness ?: return
        relevant.forEach { issue ->
            if (issue.isGranted(previous) && !issue.isGranted(readiness)) telemetry.preconditionLost(issue.name)
        }
    }

    private fun issuesFor(mode: TriggerMode?, readiness: TrackingReadiness?): List<ReadinessIssue> {
        if (readiness == null) return emptyList()
        return relevantReadinessIssues(mode).filterNot { issue -> issue.isGranted(readiness) }
    }

    private fun TrackingStatus.toStatusDetail(mode: TriggerMode?): UiText? = when (this) {
        is TrackingStatus.Disabled -> null
        is TrackingStatus.Standby -> when (mode) {
            TriggerMode.STEREO -> UiText(Res.string.ao_settings_waiting_stereo)
            TriggerMode.NO_STEREO -> UiText(Res.string.ao_settings_waiting_no_stereo)
            null -> UiText(Res.string.ao_settings_waiting_generic)
        }
        is TrackingStatus.Tracking -> if (isPaused) {
            UiText(Res.string.ao_settings_trip_paused)
        } else {
            UiText(Res.string.ao_settings_trip_in_progress)
        }
    }

    private fun send(effect: SettingsEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val TRACE_OBSERVE = "observe_settings"
        const val TRACE_MONTHLY = "load_monthly_summary"
        const val TRACE_TOGGLE = "toggle_tracking"
        const val TRACE_PAUSE = "pause_week"
        const val TRACE_RESUME = "resume_tracking"
        const val TRACE_DELETE = "delete_all_trip_data"
    }
}
