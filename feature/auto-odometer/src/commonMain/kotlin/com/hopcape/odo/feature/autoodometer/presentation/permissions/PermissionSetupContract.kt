package com.hopcape.odo.feature.autoodometer.presentation.permissions

import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.feature.autoodometer.domain.model.RecentDrive

/**
 * One permission the setup flow (M4) asks for, in the order it asks.
 *
 * [required] marks whether the step gates completion. Fine location and activity recognition are
 * load-bearing — `TriggerArming.compute` in `:core:triptracker` arms nothing without them, so
 * there is no feature to finish setting up. [BACKGROUND_LOCATION] is not: the triggers still arm
 * without it and a drive taken with the app open is still measured. What it buys is the drive
 * nobody is there for, which is the whole point — but that makes it something to argue for, not
 * something to hold the owner hostage over. The trip-logged screen's upgrade card exists to make
 * the argument again later, from a drive that actually happened.
 *
 * `POST_NOTIFICATIONS` is not a step here at all. It is a one-tap dialog, it changes nothing
 * about whether tracking works, and the education screen's third numbered line — "your odometer
 * ticks up when you park" — is the notification it is for. Asking for it on the way out of that
 * screen costs nothing; a numbered page of its own made this flow read one ask longer than it is.
 *
 * [BACKGROUND_LOCATION] comes strictly after [FINE_LOCATION], never bundled with it — Android 11+
 * refuses a combined ask outright, and Play's location policy expects the incremental order.
 *
 * [AUTOSTART] is not an Android permission at all. Xiaomi, Oppo, Vivo, Realme and their
 * relatives hold a switch of their own, off by default, that decides whether Odo may be started
 * in the background; while it is off the Bluetooth broadcast never reaches the app, nothing
 * arms, and every permission above it is beside the point (issue #272 — setup reporting success
 * on a phone that then measured nothing). Nothing can read that switch back, which is why it is
 * `required = false`: a gate on something unreadable would never clear. It is still a step with
 * a page of its own rather than a line of advice, shown only on the builds that have one.
 */
internal enum class PermissionSetupStep(val required: Boolean) {
    FINE_LOCATION(required = true),
    BACKGROUND_LOCATION(required = false),
    ACTIVITY_RECOGNITION(required = true),
    AUTOSTART(required = false),
}

/**
 * Builds the step order for [mode] — 2 for STEREO, 3 for NO_STEREO (plan §5) — plus the
 * autostart step last on the builds that hold background starts behind a switch of their own.
 *
 * [needsAutostart] comes from
 * [com.hopcape.odo.core.platform.notification.BackgroundStartAccess.needsAttention]. Last
 * because it is the only step that is not an Android permission: the system asks come first,
 * and the manufacturer's own page is the last thing between the owner and a setup that works.
 * Appended rather than always present, so the counter stays honest on a phone with no such
 * switch to turn on.
 */
internal fun stepsFor(mode: TriggerMode, needsAutostart: Boolean = false): List<PermissionSetupStep> = buildList {
    add(PermissionSetupStep.FINE_LOCATION)
    add(PermissionSetupStep.BACKGROUND_LOCATION)
    if (mode == TriggerMode.NO_STEREO) add(PermissionSetupStep.ACTIVITY_RECOGNITION)
    if (needsAutostart) add(PermissionSetupStep.AUTOSTART)
}

/**
 * Whether this step ends on a system screen worth drawing first.
 *
 * Both location asks do. Android puts them behind a list of mutually exclusive choices — "all the
 * time", "while using the app", "ask every time" — where picking the wrong row looks like
 * agreeing and leaves the feature half-working, and on Android 11+ the background one has no
 * dialog at all, only a settings page. Activity recognition is a plain yes/no dialog, so a screen
 * describing it would be a screen describing a sentence.
 */
internal val PermissionSetupStep.hasHandoff: Boolean
    get() = this == PermissionSetupStep.FINE_LOCATION ||
        this == PermissionSetupStep.BACKGROUND_LOCATION

/**
 * One step's progress through the flow.
 *
 * [askedOnce] is what tells a fresh step (never requested — show the rationale) apart from a step
 * the owner already answered and declined (show the denial row too) — Android reports both as
 * [PermissionStatus.Askable] ("the system will still show its dialog"), so the status alone
 * cannot draw that line.
 */
internal data class PermissionStepState(
    val step: PermissionSetupStep,
    val status: PermissionStatus = PermissionStatus.Askable,
    val askedOnce: Boolean = false,
)

/**
 * Display state for permission setup (M4): an ordered, per-step state machine rather than a flat
 * boolean bag (docs/AUTO_ODOMETER_PLAN.md §4.2's "not scattered launches"). [currentIndex] is the
 * step on screen; earlier steps are resolved (granted, or skipped when optional), later steps
 * have not been visited yet.
 */
internal data class PermissionSetupUiState(
    val mode: TriggerMode = TriggerMode.STEREO,
    val steps: List<PermissionStepState> = emptyList(),
    val currentIndex: Int = 0,
    /**
     * Whether the drawing of the system screen is showing instead of the step's rationale.
     *
     * A second page of the same step rather than a step of its own, so it never moves the
     * counter — being told what a screen looks like is not another thing being asked for.
     */
    val onHandoff: Boolean = false,
    /**
     * The owner's last few drives, newest first, or empty when there are none to show.
     *
     * Empty during a first-time setup, which is the normal case: education, picker and
     * permissions all happen before the car has moved once. It fills only for an owner who
     * declined background location, drove, and came back — and then it is the strongest thing
     * this flow can say, because the missed drives are theirs.
     */
    val recentDrives: List<RecentDrive> = emptyList(),
    /** True while [com.hopcape.odo.feature.autoodometer.domain.usecase.CompleteSetup] is running. */
    val completing: Boolean = false,
    /**
     * False once the autostart hand-off was tried and no page could be opened — those
     * activities are not API and differ between builds. The step then says where to look by
     * hand instead of leaving the owner on a button that did nothing.
     */
    val autostartPageFound: Boolean = true,
) {
    /** The step on screen, or null once every step has resolved (mid-completion). */
    val current: PermissionStepState? get() = steps.getOrNull(currentIndex)

    /** 1-based position for the step counter. */
    val stepNumber: Int get() = currentIndex + 1
    val totalSteps: Int get() = steps.size

    /** The system will no longer prompt for [current] — the button offers settings instead. */
    val currentBlocked: Boolean get() = current?.status == PermissionStatus.Blocked

    /**
     * [current] is unambiguously not granted: permanently blocked (no ambiguity — always shown),
     * or asked at least once and still only [PermissionStatus.Askable] (a first-ever read of that
     * same status is the rationale, not a denial — see [PermissionStepState]).
     */
    val showDenialRow: Boolean
        get() = current?.let {
            // Autostart has no status to be denied. Its `askedOnce` means "already sent to the
            // manufacturer's page", and a row reading "Autostart was denied" would be stating
            // as fact the one thing nothing on the phone can tell us.
            it.step != PermissionSetupStep.AUTOSTART &&
                it.status != PermissionStatus.Granted &&
                (it.status == PermissionStatus.Blocked || it.askedOnce)
        } == true

    /**
     * Whether the step on screen offers a way past it without granting.
     *
     * Optional steps always do, from the moment their page opens rather than only after a
     * refusal: background location's whole page is an argument, and an argument the owner cannot
     * decline is a demand.
     */
    val showSkip: Boolean get() = current?.step?.required == false

    /**
     * Whether the drawing of the system screen is what should be on screen right now.
     *
     * [onHandoff] alone is not enough — a step with no system screen to draw has nothing to show,
     * and the flag can survive a step change.
     */
    val showHandoff: Boolean get() = onHandoff && current?.step?.hasHandoff == true

    /** The step on screen is the manufacturer's autostart switch, which no permission covers. */
    val onAutostartStep: Boolean get() = current?.step == PermissionSetupStep.AUTOSTART

    /**
     * The owner has been sent to the manufacturer's page at least once.
     *
     * Reuses the step's [PermissionStepState.askedOnce] — the same "we have handed off once"
     * flag every other step keeps — because that page is this step's version of a system
     * screen. What it changes is what the page asks: after the hand-off the primary button
     * stops offering the trip and starts asking whether the switch is on, which is the only
     * answer that will ever exist for it.
     */
    val autostartOpened: Boolean get() = onAutostartStep && current?.askedOnce == true
}

/** What the owner did during permission setup, as data. */
internal sealed interface PermissionSetupEvent {

    /**
     * A permission controller's status, as read at the route host. Fires on first composition (so
     * an already-granted permission is picked up without a tap) and on every recomposition after
     * the system dialog or the settings page answers.
     */
    data class StatusObserved(val step: PermissionSetupStep, val status: PermissionStatus) :
        PermissionSetupEvent

    /**
     * The primary button, on whichever page it was pressed.
     *
     * On a rationale page for a step with a system screen it moves to the drawing; everywhere
     * else it is the ask itself, which the route host performs because only it holds the
     * platform controller.
     *
     * On [PermissionSetupStep.AUTOSTART] the button means two things in turn, matching what it
     * says at the time: the first press opens the manufacturer's page, the next is the owner
     * saying the switch is on. There is no controller and no dialog — the ViewModel performs
     * that hand-off itself, through `BackgroundStartAccess`.
     */
    data object ContinueTapped : PermissionSetupEvent

    /**
     * The dismiss on an optional step — "keep it to while using", or "Not now" on autostart,
     * where it is the owner declining in front of the explanation rather than a step nobody
     * showed them.
     */
    data object SkipTapped : PermissionSetupEvent

    /** Back arrow: off the drawing and back to the step's own page, or out of the flow. */
    data object BackTapped : PermissionSetupEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface PermissionSetupEffect {

    /** Pops back to wherever the screen was entered from. */
    data object NavigateBack : PermissionSetupEffect

    /**
     * Ask the OS for [step], or open settings when it will not prompt again.
     *
     * An effect rather than something the ViewModel does, because requesting a permission needs
     * the Activity and only a composable can reach it.
     */
    data class RequestPermission(val step: PermissionSetupStep, val blocked: Boolean) :
        PermissionSetupEffect

    /**
     * Every step is resolved (or the optional one was declined): tracking is on, pop the whole
     * auto-odometer flow and land back on the garage tab
     * (docs/AUTO_ODOMETER_PLAN.md's locked navigation-flow decision).
     */
    data object NavigateToGarage : PermissionSetupEffect
}
