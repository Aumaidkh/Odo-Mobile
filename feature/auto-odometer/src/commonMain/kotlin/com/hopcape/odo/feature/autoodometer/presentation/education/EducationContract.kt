package com.hopcape.odo.feature.autoodometer.presentation.education

import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.triptracker.TriggerMode

/**
 * Display state for the education screen (M2). The screen is static content keyed off
 * [mode] — no loading or error state exists to model, so this is the whole state.
 *
 * [notifications] is not drawn anywhere. It is held only to decide, on the CTA, whether the
 * notification step is worth showing: this screen names no permission on purpose, and adding
 * one to it would undo the reason it exists.
 */
internal data class EducationUiState(
    val mode: TriggerMode = TriggerMode.STEREO,
    val notifications: PermissionStatus = PermissionStatus.Granted,
) {

    /**
     * Whether the notification step has anything to offer.
     *
     * Only when the system will still prompt. Granted needs no case made for it, and Blocked
     * cannot be answered without a trip to the app's settings page — turning "Pair my car" into
     * that trip is not a fair reading of the button, the same call the old inline ask already
     * made and the one thing about it that was right.
     */
    val shouldExplainNotifications: Boolean get() = notifications == PermissionStatus.Askable
}

/** What the owner did on the education screen, as data. */
internal sealed interface EducationEvent {

    /** "Pair my car" (STEREO) / "Turn it on" (NO_STEREO). */
    data object CtaTapped : EducationEvent

    /** `POST_NOTIFICATIONS` as the route host reads it — on entry and on every change. */
    data class NotificationStatusObserved(val status: PermissionStatus) : EducationEvent

    /** The `X` in the top bar. */
    data object CloseTapped : EducationEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface EducationEffect {

    /**
     * The CTA, when the system would still prompt for `POST_NOTIFICATIONS` — make the case for
     * the note on a page of its own before anything is asked.
     */
    data class NavigateToNotificationRationale(val mode: TriggerMode) : EducationEffect

    /** STEREO's CTA — on to the trigger-device picker (M3). */
    data object NavigateToDevicePicker : EducationEffect

    /**
     * NO_STEREO's CTA — straight to permission setup (M4); enrollment for this path
     * happens inside that flow (F6), not here.
     */
    data class NavigateToPermissionSetup(val mode: TriggerMode) : EducationEffect

    /** Pops to wherever the screen was entered from (garage or the device picker). */
    data object NavigateBack : EducationEffect
}
