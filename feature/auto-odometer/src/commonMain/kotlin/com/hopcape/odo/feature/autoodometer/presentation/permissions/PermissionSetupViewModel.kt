package com.hopcape.odo.feature.autoodometer.presentation.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.platform.notification.BackgroundStartAccess
import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.feature.autoodometer.domain.usecase.CompleteSetup
import com.hopcape.odo.feature.autoodometer.domain.usecase.EnrollTriggerDevice
import com.hopcape.odo.feature.autoodometer.presentation.AutoOdometerTelemetry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the permission checklist (M4) — a small ordered state machine, not
 * scattered permission launches (docs/AUTO_ODOMETER_PLAN.md §4.2).
 *
 * The route host owns every [com.hopcape.odo.core.platform.permission.PermissionController]
 * (one per step, exactly like [com.hopcape.odo.feature.autoodometer.presentation.devicepicker.DevicePickerViewModel]'s
 * single Bluetooth controller) and reports status back in via
 * [PermissionSetupEvent.StatusObserved]; this class only decides which step is on screen and
 * when the checklist is done. It never touches a platform permission API directly.
 *
 * Notifications is soft-required: declined or blocked, it still lets the checklist finish,
 * because `TrackingReadiness.canTrack` in `:core:triptracker` never reads it — only fine
 * location (and activity recognition on the NO_STEREO path) actually gate tracking. Fine
 * location and activity recognition are load-bearing, so the checklist stays on that step
 * until it is granted.
 */
internal class PermissionSetupViewModel(
    mode: TriggerMode,
    private val enrollTriggerDevice: EnrollTriggerDevice,
    private val completeSetup: CompleteSetup,
    private val activeCar: ActiveCarProvider,
    private val backgroundStart: BackgroundStartAccess,
    private val telemetry: AutoOdometerTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(
        PermissionSetupUiState(
            mode = mode,
            steps = stepsFor(mode, needsAutostart = backgroundStart.needsAttention())
                .map { PermissionStepState(step = it) },
        ),
    )
    val state: StateFlow<PermissionSetupUiState> = _state.asStateFlow()

    private val _effects = Channel<PermissionSetupEffect>(Channel.BUFFERED)
    val effects: Flow<PermissionSetupEffect> = _effects.receiveAsFlow()

    fun onEvent(event: PermissionSetupEvent) {
        when (event) {
            is PermissionSetupEvent.StatusObserved -> statusObserved(event.step, event.status)
            PermissionSetupEvent.ContinueTapped -> continueTapped()
            PermissionSetupEvent.SkipTapped -> skipTapped()
            PermissionSetupEvent.BackTapped -> send(PermissionSetupEffect.NavigateBack)
        }
    }

    /**
     * A step's controller status, as read (or re-read) at the route host. Fires on first
     * composition — so a permission already granted before this screen is reached is picked up
     * without a tap — and again whenever the system dialog or the settings page answers.
     */
    private fun statusObserved(step: PermissionSetupStep, status: PermissionStatus) {
        val index = _state.value.steps.indexOfFirst { it.step == step }
        if (index < 0) return
        val previous = _state.value.steps[index]
        if (previous.status == status) return

        telemetry.permissionAnswered(step = step.name, status = status.name)
        _state.update { s -> s.copy(steps = s.steps.replaceStatus(index, status)) }

        when {
            status == PermissionStatus.Granted && index == _state.value.currentIndex -> advanceFrom(index)
            wasGrantedStepRevoked(previous, status, index) -> resurfaceRevokedStep(index)
        }
    }

    /** A step that already let the checklist move past it got turned off outside the app. */
    private fun wasGrantedStepRevoked(previous: PermissionStepState, status: PermissionStatus, index: Int): Boolean =
        status != PermissionStatus.Granted && previous.status == PermissionStatus.Granted && index < _state.value.currentIndex

    /**
     * The owner turned a permission off in system settings (or an OEM permission auto-reset
     * happened) — the checklist must not keep trusting a stale "granted" answer that let it
     * advance past this step, so it comes back on screen instead.
     */
    private fun resurfaceRevokedStep(index: Int) {
        _state.update { it.copy(currentIndex = index) }
    }

    /**
     * The primary CTA. Marks [current][PermissionSetupUiState.current] as attempted so a
     * non-grant answer shows the denial row instead of the priming card again; for a permission
     * step the actual system dialog / settings hand-off is fired by the route host, which is
     * the only layer holding the platform controller. Autostart has no controller and is
     * handled here — see [openAutostartPage].
     */
    private fun continueTapped() {
        val index = _state.value.currentIndex
        val current = _state.value.current ?: return
        _state.update { s -> s.copy(steps = s.steps.replaceAskedOnce(index)) }
        if (!current.step.isPermission) openAutostartPage(index)
    }

    /**
     * Autostart is the one step with no answer to wait for: the manufacturer's switch cannot be
     * read back, so the checklist opens the page and moves on rather than parking on a step it
     * can never resolve. [BackgroundStartAccess.open] reports whether any page was found —
     * false means this build hid it somewhere the intents do not reach, which is recorded and
     * otherwise treated the same, since staying here would strand the owner either way.
     */
    private fun openAutostartPage(index: Int) {
        val opened = backgroundStart.open()
        telemetry.permissionAnswered(step = PermissionSetupStep.AUTOSTART.name, status = autostartOutcome(opened))
        advanceFrom(index)
    }

    private fun autostartOutcome(opened: Boolean): String = if (opened) OUTCOME_OPENED else OUTCOME_NO_PAGE

    private fun skipTapped() {
        val current = _state.value.current ?: return
        if (current.step.required) return
        advanceFrom(_state.value.currentIndex)
    }

    /** Moves past [index], skipping over any later step that is already granted, then completes. */
    private fun advanceFrom(index: Int) {
        var next = index + 1
        while (next < _state.value.steps.size && _state.value.steps[next].status == PermissionStatus.Granted) {
            next++
        }
        _state.update { it.copy(currentIndex = next) }
        if (next >= _state.value.steps.size) completeFlow()
    }

    private fun completeFlow() {
        if (_state.value.completing) return
        _state.update { it.copy(completing = true) }
        viewModelScope.launch(telemetry.op(TRACE_COMPLETE)) {
            val mode = _state.value.mode
            if (mode == TriggerMode.NO_STEREO && !enrollNoStereoDevice()) return@launch
            finishSetup(mode)
        }
    }

    /**
     * Only STEREO enrolls at the device picker (F5); NO_STEREO has no device to bond to and
     * locks its trigger mode in here instead (F6's known gap). Returns `false` (and resets
     * the checklist to a retryable state) if there's no active car or enrollment fails.
     */
    private suspend fun enrollNoStereoDevice(): Boolean {
        val carId = activeCar.activeCarId.value
        if (carId == null) {
            telemetry.noActiveCar()
            _state.update { it.copy(completing = false) }
            return false
        }
        // EnrollTriggerDevice has no Either wrapper (F1's port signature is a plain suspend
        // fun) — a thrown exception here is unmodeled and means something is broken, the
        // same reasoning as the device picker's catalog read. Caught so the checklist resets
        // to a retryable state instead of crashing the screen.
        val enrolled = runCatching {
            enrollTriggerDevice(carId = carId, bluetoothId = "", mode = TriggerMode.NO_STEREO)
        }.onFailure { telemetry.nonFatal(it, stage = STAGE_ENROLL) }
        if (enrolled.isFailure) {
            _state.update { it.copy(completing = false) }
            return false
        }
        return true
    }

    private suspend fun finishSetup(mode: TriggerMode) {
        completeSetup()
        telemetry.setupCompleted(mode.name)
        send(PermissionSetupEffect.NavigateToGarage)
    }

    private fun send(effect: PermissionSetupEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val TRACE_COMPLETE = "complete_setup"
        const val STAGE_ENROLL = "enroll_trigger_device"
        const val OUTCOME_OPENED = "OPENED"
        const val OUTCOME_NO_PAGE = "NO_PAGE"
    }
}

private fun List<PermissionStepState>.replaceStatus(index: Int, status: PermissionStatus): List<PermissionStepState> =
    mapIndexed { i, s -> if (i == index) s.copy(status = status) else s }

private fun List<PermissionStepState>.replaceAskedOnce(index: Int): List<PermissionStepState> =
    mapIndexed { i, s -> if (i == index) s.copy(askedOnce = true) else s }
