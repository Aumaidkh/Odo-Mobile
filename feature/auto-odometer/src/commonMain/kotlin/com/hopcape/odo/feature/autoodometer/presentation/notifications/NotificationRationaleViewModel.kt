package com.hopcape.odo.feature.autoodometer.presentation.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.triptracker.TriggerMode
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
 * State holder for the notification step — Odo's own case for `POST_NOTIFICATIONS`, made
 * before the system asks rather than instead of it.
 *
 * The step exists because the ask used to be fired on the way out of the education screen,
 * which put the system dialog on screen with the Bluetooth rationale already drawn behind it:
 * two questions at once, one of them never introduced. It is only ever reached when the system
 * will actually prompt, so this class never has to model "already granted" or "will never ask
 * again" — the education route filters both out before navigating here.
 *
 * Answering either way moves on. The note is not a precondition for tracking
 * (`TrackingReadiness.canTrack` never reads it), so a refusal costs the owner the live
 * distance and its controls, not the feature.
 */
internal class NotificationRationaleViewModel(
    mode: TriggerMode,
    private val telemetry: AutoOdometerTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationRationaleUiState(mode = mode))
    val state: StateFlow<NotificationRationaleUiState> = _state.asStateFlow()

    private val _effects = Channel<NotificationRationaleEffect>(Channel.BUFFERED)
    val effects: Flow<NotificationRationaleEffect> = _effects.receiveAsFlow()

    init {
        telemetry.notificationRationaleViewed()
    }

    fun onEvent(event: NotificationRationaleEvent) {
        when (event) {
            is NotificationRationaleEvent.StatusObserved -> statusObserved(event.status)
            NotificationRationaleEvent.AllowTapped -> allowTapped()
            NotificationRationaleEvent.SkipTapped -> skipTapped()
            NotificationRationaleEvent.BackTapped -> send(NotificationRationaleEffect.NavigateBack)
        }
    }

    private fun allowTapped() {
        _state.update { it.copy(asked = true) }
        send(NotificationRationaleEffect.RequestPermission)
    }

    /**
     * Moves on once the dialog this screen raised has been answered.
     *
     * Guarded on [NotificationRationaleUiState.asked] because this also fires on entry, with
     * the same `Askable` the screen is here to act on — advancing on that would skip the step
     * the moment it appeared. A dialog dismissed without an answer leaves the status where it
     * was, which correctly leaves the owner here to decide again.
     */
    private fun statusObserved(status: PermissionStatus) {
        if (!_state.value.asked || status == PermissionStatus.Askable) return
        telemetry.permissionAnswered(step = STEP_NOTIFICATIONS, status = status.name)
        onward()
    }

    private fun skipTapped() {
        telemetry.permissionAnswered(step = STEP_NOTIFICATIONS, status = STATUS_SKIPPED)
        onward()
    }

    /** The branch education used to make on its CTA, now made one step later. */
    private fun onward() {
        val effect = when (_state.value.mode) {
            TriggerMode.STEREO -> NotificationRationaleEffect.NavigateToDevicePicker
            TriggerMode.NO_STEREO -> NotificationRationaleEffect.NavigateToPermissionSetup(TriggerMode.NO_STEREO)
        }
        send(effect)
    }

    private fun send(effect: NotificationRationaleEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val STEP_NOTIFICATIONS = "post_notifications"

        /** Not a [PermissionStatus] — the owner passed the step without the system being asked. */
        const val STATUS_SKIPPED = "SKIPPED"
    }
}
