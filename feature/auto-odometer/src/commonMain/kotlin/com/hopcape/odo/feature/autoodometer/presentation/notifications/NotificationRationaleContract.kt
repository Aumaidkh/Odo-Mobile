package com.hopcape.odo.feature.autoodometer.presentation.notifications

import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.triptracker.TriggerMode

/**
 * Display state for the notification step. The copy is fixed, so [mode] only decides where
 * the step hands off to, and [asked] only decides whether an incoming status counts as an
 * answer to this screen's own question.
 */
internal data class NotificationRationaleUiState(
    val mode: TriggerMode = TriggerMode.STEREO,
    /** True once the owner pressed the confirm button and the system was asked. */
    val asked: Boolean = false,
)

/** What the owner did on the notification step, as data. */
internal sealed interface NotificationRationaleEvent {

    /**
     * `POST_NOTIFICATIONS` as the route host reads it — on entry, and again once the system
     * dialog is answered.
     */
    data class StatusObserved(val status: PermissionStatus) : NotificationRationaleEvent

    /** The confirm button — raise the system dialog. */
    data object AllowTapped : NotificationRationaleEvent

    /** "Not now" — carry on without the note. */
    data object SkipTapped : NotificationRationaleEvent

    /** The back arrow. */
    data object BackTapped : NotificationRationaleEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface NotificationRationaleEffect {

    /** Show the system permission dialog. */
    data object RequestPermission : NotificationRationaleEffect

    /** STEREO — on to the trigger-device picker, the same place education used to go. */
    data object NavigateToDevicePicker : NotificationRationaleEffect

    /** NO_STEREO — straight to permission setup, mirroring education's own branch. */
    data class NavigateToPermissionSetup(val mode: TriggerMode) : NotificationRationaleEffect

    /** Back to the explainer. */
    data object NavigateBack : NotificationRationaleEffect
}
