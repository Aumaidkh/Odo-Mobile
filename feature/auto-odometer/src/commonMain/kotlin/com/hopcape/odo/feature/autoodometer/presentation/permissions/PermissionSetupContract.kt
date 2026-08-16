package com.hopcape.odo.feature.autoodometer.presentation.permissions

import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.triptracker.TriggerMode

/**
 * One permission the checklist (M4) can ask for, in the order they are shown.
 *
 * [required] marks whether this step gates completion. Notifications is soft-required — the
 * feature keeps working without it (`TrackingReadiness.canTrack` never reads it, see
 * `:core:triptracker`'s `TrackingReadiness`) — so a declined or blocked notifications step
 * still lets the checklist finish. Fine location, background location (and activity
 * recognition on the NO_STEREO path) are load-bearing: without them there is nothing for
 * the engine to track with, so the checklist stays on that step until it is granted.
 *
 * [BACKGROUND_LOCATION] comes strictly after [FINE_LOCATION], never bundled with it —
 * Android 11+ refuses a combined ask outright, and Play's location policy expects the
 * incremental order. The step's priming card is the in-app rationale the policy requires
 * before the system's own screen: on Android 11+ the request opens the app's location
 * settings page (there is no dialog any more), where the owner picks "Allow all the time".
 * Without it a trip only ever starts with the app open, which defeats the feature — this
 * used to be deferred to the trip-logged screen as an upgrade prompt, and field use showed
 * owners never got there: the first background trip silently never started.
 */
internal enum class PermissionSetupStep(val required: Boolean) {
    NOTIFICATIONS(required = false),
    FINE_LOCATION(required = true),
    BACKGROUND_LOCATION(required = true),
    ACTIVITY_RECOGNITION(required = true),
}

/** Builds the step order for [mode] — 3 steps for STEREO, 4 for NO_STEREO (plan §5). */
internal fun stepsFor(mode: TriggerMode): List<PermissionSetupStep> = buildList {
    add(PermissionSetupStep.NOTIFICATIONS)
    add(PermissionSetupStep.FINE_LOCATION)
    add(PermissionSetupStep.BACKGROUND_LOCATION)
    if (mode == TriggerMode.NO_STEREO) add(PermissionSetupStep.ACTIVITY_RECOGNITION)
}

/**
 * One step's progress through the checklist.
 *
 * [askedOnce] is what tells a fresh step (never requested — show the priming card) apart from
 * a step the owner already answered and declined (show the denial row instead) — Android
 * reports both as [PermissionStatus.Askable] ("the system will still show its dialog"), so the
 * status alone cannot draw that line.
 */
internal data class PermissionStepState(
    val step: PermissionSetupStep,
    val status: PermissionStatus = PermissionStatus.Askable,
    val askedOnce: Boolean = false,
)

/**
 * Display state for the permission checklist (M4): an ordered, per-step state machine rather
 * than a flat boolean bag (docs/AUTO_ODOMETER_PLAN.md §4.2's "not scattered launches").
 * [currentIndex] is the step on screen; earlier steps are resolved (granted, or skipped when
 * optional), later steps have not been visited yet.
 */
internal data class PermissionSetupUiState(
    val mode: TriggerMode = TriggerMode.STEREO,
    val steps: List<PermissionStepState> = emptyList(),
    val currentIndex: Int = 0,
    /** True while [com.hopcape.odo.feature.autoodometer.domain.usecase.CompleteSetup] is running. */
    val completing: Boolean = false,
) {
    /** The step on screen, or null once every step has resolved (mid-completion). */
    val current: PermissionStepState? get() = steps.getOrNull(currentIndex)

    /** 1-based position for the "Step X of N" progress line. */
    val stepNumber: Int get() = currentIndex + 1
    val totalSteps: Int get() = steps.size

    /** The system will no longer prompt for [current] — the row offers settings instead. */
    val currentBlocked: Boolean get() = current?.status == PermissionStatus.Blocked

    /**
     * [current] is unambiguously not granted: permanently blocked (no ambiguity — always
     * shown), or asked at least once and still only [PermissionStatus.Askable] (a first-ever
     * read of that same status is the priming card, not a denial — see [PermissionStepState]).
     */
    val showDenialRow: Boolean
        get() = current?.let { it.status != PermissionStatus.Granted && (it.status == PermissionStatus.Blocked || it.askedOnce) } == true

    /** Only an unresolved, optional step offers a way past it, and only once its row shows. */
    val showSkip: Boolean get() = showDenialRow && current?.step?.required == false
}

/** What the owner did on the permission checklist, as data. */
internal sealed interface PermissionSetupEvent {

    /**
     * A permission controller's status, as read at the route host. Fires on first composition
     * (so an already-granted permission is picked up without a tap) and on every recomposition
     * after the system dialog or the settings page answers.
     */
    data class StatusObserved(val step: PermissionSetupStep, val status: PermissionStatus) : PermissionSetupEvent

    /** The primary CTA — "Finish setup", or "Open settings" once [current][PermissionSetupUiState.currentBlocked]. */
    data object ContinueTapped : PermissionSetupEvent

    /** "Skip for now" on an unresolved, optional step's denial row. */
    data object SkipTapped : PermissionSetupEvent

    /** Back arrow. */
    data object BackTapped : PermissionSetupEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface PermissionSetupEffect {

    /** Pops back to wherever the screen was entered from. */
    data object NavigateBack : PermissionSetupEffect

    /**
     * The checklist went all-green (or every optional step was skipped): tracking is on, pop
     * the whole auto-odometer flow and land back on the garage tab
     * (docs/AUTO_ODOMETER_PLAN.md's locked navigation-flow decision).
     */
    data object NavigateToGarage : PermissionSetupEffect
}
