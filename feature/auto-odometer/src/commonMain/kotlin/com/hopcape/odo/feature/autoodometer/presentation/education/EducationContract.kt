package com.hopcape.odo.feature.autoodometer.presentation.education

import com.hopcape.odo.core.triptracker.TriggerMode

/**
 * Display state for the education screen (M2). The screen is static content keyed off
 * [mode] — no loading or error state exists to model, so this is the whole state.
 */
internal data class EducationUiState(
    val mode: TriggerMode = TriggerMode.STEREO,
)

/** What the owner did on the education screen, as data. */
internal sealed interface EducationEvent {

    /** "Pair my car" (STEREO) / "Turn it on" (NO_STEREO). */
    data object CtaTapped : EducationEvent

    /** The `X` in the top bar. */
    data object CloseTapped : EducationEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface EducationEffect {

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
