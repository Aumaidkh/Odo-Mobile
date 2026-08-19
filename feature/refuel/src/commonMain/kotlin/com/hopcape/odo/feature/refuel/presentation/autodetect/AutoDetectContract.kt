package com.hopcape.odo.feature.refuel.presentation.autodetect

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.refuel.DetectionApp
import com.hopcape.odo.core.domain.refuel.IgnoredMerchant
import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.platform.permission.isGranted

/**
 * The two things auto-detect needs a screen of its own to ask for.
 *
 * They are easy to mistake for one switch and they are not. [Access] lets Odo *read* the
 * notification a payment app posted; [Background] decides whether the phone will wake Odo up to
 * do it. Granting either alone detects nothing, so each is asked for on its own page that says
 * what that one does.
 *
 * `POST_NOTIFICATIONS` is not among them, though detection needs it too. It is a one-tap system
 * dialog, and the pitch page already draws the notification it is for — a numbered screen of its
 * own said the same thing a second time and made the flow read as longer than it is. It is asked
 * for on the way out of that page instead.
 *
 * The order is deliberate: [Access] is the one whose own system page warns that Odo will be able
 * to read every notification, and [Background] is last because it is the only one that changes
 * nothing today — it decides whether detection still works next week.
 */
internal enum class AutoDetectStep {

    /** Notification access — the listener permission detection actually runs on. */
    Access,

    /**
     * The phone's own background-start or battery setting.
     *
     * Unlike [Access] there is no API that asks for it and none that reads it back. It is a page
     * the owner walks to. So it can never be a gate — only a step the flow walks them through
     * and then takes their word for.
     */
    Background,
}

/** The first page of the flow that belongs to this step. */
internal val AutoDetectStep.firstPage: AutoDetectPage
    get() = when (this) {
        AutoDetectStep.Access -> AutoDetectPage.Access
        AutoDetectStep.Background -> AutoDetectPage.Background
    }

/**
 * One screen of the opt-in.
 *
 * It used to be a single scroll: the pitch, four privacy promises, an on-device note, a
 * checklist, a warning about the OEM permission screen and two buttons. Every part earned its
 * place and together they were more than anyone reads, which on a screen asking for a sensitive
 * permission is the opposite of informed consent — a wall of text gets skipped, and skipped text
 * persuades nobody.
 *
 * So it is one question per screen. Both asks get a second screen of their own ([AccessHandoff],
 * [BackgroundHandoff]) because they end in a system settings page rather than a dialog: the owner
 * leaves the app, has to find Odo in a list, and meets a warning Odo did not write. Saying what
 * that page looks like and what it will claim, immediately before the handoff, is the only place
 * that information is any use.
 */
internal enum class AutoDetectPage {

    /**
     * What detection is for. No permission is named here — but leaving this page is what asks
     * for `POST_NOTIFICATIONS`, because the notification drawn above the button is the whole
     * explanation that ask needs.
     */
    Why,

    /** Step 1 — the permission detection reads on. */
    Access,

    /** What the notification-access settings page looks like, before being sent to it. */
    AccessHandoff,

    /** Step 2 — the phone's own setting for waking Odo in the background. */
    Background,

    /** What the autostart or battery page looks like, before being sent to it. */
    BackgroundHandoff,
}

/** Whether this page is one of the numbered asks, and which. */
internal val AutoDetectPage.step: AutoDetectStep?
    get() = when (this) {
        AutoDetectPage.Why -> null
        AutoDetectPage.Access, AutoDetectPage.AccessHandoff -> AutoDetectStep.Access
        AutoDetectPage.Background, AutoDetectPage.BackgroundHandoff -> AutoDetectStep.Background
    }

/** What the owner did on the auto-detect screen. */
internal sealed interface AutoDetectEvent {

    /** The master switch in the settings body. */
    data class DetectionToggled(val enabled: Boolean) : AutoDetectEvent

    /**
     * `POST_NOTIFICATIONS` as the route host reads it.
     *
     * Fires on first composition and again every time the system dialog or the settings page
     * answers. Nothing in the flow waits on it — it is what the settings body reads to tell an
     * owner that detection is on and its one output has nowhere to go.
     */
    data class NotifyStatusObserved(val status: PermissionStatus) : AutoDetectEvent

    data class AppToggled(val packageName: String, val enabled: Boolean) : AutoDetectEvent

    data class ConfirmBeforeLogToggled(val enabled: Boolean) : AutoDetectEvent

    data class PredictOdometerToggled(val enabled: Boolean) : AutoDetectEvent

    /** "Ask again" on a merchant they had rejected. */
    data class MerchantUnignored(val key: String) : AutoDetectEvent

    /** Take me to the system page where notification access is granted. */
    data object OpenAccessSettings : AutoDetectEvent

    /**
     * The nudge in the settings body, when notifications have been revoked since opt-in.
     *
     * Separate from [ContinueTapped] because it is pressed with the opt-in long finished, where
     * there is no page to advance — the only thing it means is "ask me for that permission
     * again".
     */
    data object NotifyFixTapped : AutoDetectEvent

    /**
     * The primary button on whatever opt-in page is showing.
     *
     * One event rather than one per page, because to the owner it is one thing — "get me to the
     * next step". Which step that is comes from [AutoDetectUiState.page]. Leaving the pitch page
     * also raises the notifications dialog, which the route host performs, because asking needs
     * a permission controller that only a composable can hold.
     */
    data object ContinueTapped : AutoDetectEvent

    /** Back, on any page but the first: walk the flow rather than leaving it. */
    data object BackTapped : AutoDetectEvent

    /**
     * "Turn on without this" on the background step.
     *
     * Only offered on the last step, and it really does turn detection on. By that point both
     * Android permissions are granted, so dismissing instead would leave the owner having
     * granted a sensitive permission and got nothing for it.
     */
    data object BackgroundSkipped : AutoDetectEvent

    /** Take me to this phone's own autostart page. */
    data object OpenAutostartSettings : AutoDetectEvent

    /** "I've done this" on the autostart card in settings. */
    data object AutostartAcknowledged : AutoDetectEvent

    /** The screen came back to the foreground — re-read the permissions. */
    data object Resumed : AutoDetectEvent

    data object NotNowTapped : AutoDetectEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface AutoDetectEffect {

    data object Dismiss : AutoDetectEffect

    /**
     * Ask for `POST_NOTIFICATIONS`, or open app settings when the system will not prompt again.
     *
     * An effect rather than something the ViewModel does, because the ask needs the Activity.
     */
    data class RequestNotifyPermission(val blocked: Boolean) : AutoDetectEffect
}

/**
 * The auto-detect screen's state.
 *
 * One screen serves two jobs, and [optedIn] is what switches between them. Before the owner has
 * turned anything on it is the explanation — what would be read, what would not, and that none
 * of it leaves the phone. Afterwards it is the settings, including the way back out.
 *
 * [accessGranted] is read from the OS, not from Odo's own switch, because the two can disagree:
 * the owner can revoke notification access in system settings at any time, and a screen showing
 * "on" while nothing is being read would be lying.
 */
@Immutable
internal data class AutoDetectUiState(
    val loading: Boolean = true,
    val optedIn: Boolean = false,
    val accessGranted: Boolean = false,
    /**
     * `POST_NOTIFICATIONS`, read from the OS at the route host.
     *
     * Separate from [accessGranted] because they are separate permissions with separate failure
     * modes: this one has a system dialog Odo can trigger, the other only has a settings page
     * Odo can open.
     */
    val notifyStatus: PermissionStatus = PermissionStatus.Askable,
    val confirmBeforeLog: Boolean = true,
    val predictOdometer: Boolean = true,
    val apps: List<DetectionApp> = emptyList(),
    val ignoredMerchants: List<IgnoredMerchant> = emptyList(),
    val detectedFillCount: Int = 0,
    /** Whether the owner has said they dealt with the phone's background-start setting. */
    val autostartAcknowledged: Boolean = false,
    /**
     * Whether this phone holds background starts behind a switch of its own.
     *
     * Only gates the reminder card in the settings body. The step in the opt-in is shown on
     * every phone, because every phone sleeps apps to save battery — this flag is about the
     * manufacturers that go further and refuse to start Odo at all, where a standing reminder
     * is worth the nagging.
     */
    val needsAutostart: Boolean = false,
    /** Which page of the opt-in is on screen. Ignored once [optedIn]. */
    val page: AutoDetectPage = AutoDetectPage.Why,
    /**
     * The asks this run of the flow will make, fixed when the owner leaves [AutoDetectPage.Why].
     *
     * A snapshot rather than a live list, so the counter cannot renumber itself underneath the
     * owner: granting step one would otherwise turn "2 of 3" into "1 of 2" while they were
     * looking at it, which reads as the flow growing a screen rather than losing one.
     */
    val steps: List<AutoDetectStep> = emptyList(),
) {
    /**
     * Which asks still have anything owed on them, right now.
     *
     * What [steps] is taken from, and what the first page counts to tell the owner how long
     * this will be before they start.
     */
    val pendingSteps: List<AutoDetectStep>
        get() = buildList {
            if (!accessGranted) add(AutoDetectStep.Access)
            if (!autostartAcknowledged) add(AutoDetectStep.Background)
        }

    /**
     * Whether leaving the pitch page should raise the notifications dialog.
     *
     * Only when the system will actually show one. Blocked means it will not, and hijacking a
     * button that says "see what it needs" into a trip to app settings is not a fair reading of
     * it — the settings body says so plainly once detection is on instead.
     */
    val notifyAskPending: Boolean get() = notifyStatus == PermissionStatus.Askable

    /** 1-based position of the page on screen among [steps]; 0 on a page that is not a step. */
    val stepNumber: Int get() = steps.indexOf(page.step).let { if (it < 0) 0 else it + 1 }

    /** How many asks the counter is out of. Zero on [AutoDetectPage.Why], which has no counter. */
    val stepTotal: Int get() = if (page.step == null) 0 else steps.size

    /**
     * Where back goes from here, or null when back should leave the screen.
     *
     * Walks [steps] rather than the full page list, so an owner whose phone had already granted
     * notifications is never reversed onto a screen asking for it.
     */
    val previousPage: AutoDetectPage?
        get() = when (page) {
            AutoDetectPage.Why -> null
            AutoDetectPage.Access -> pageBefore(AutoDetectStep.Access)
            AutoDetectPage.AccessHandoff -> AutoDetectPage.Access
            AutoDetectPage.Background -> pageBefore(AutoDetectStep.Background)
            AutoDetectPage.BackgroundHandoff -> AutoDetectPage.Background
        }

    /** The last page of the step before [step], or the pitch when it is the first one. */
    private fun pageBefore(step: AutoDetectStep): AutoDetectPage =
        when (steps.getOrNull(steps.indexOf(step) - 1)) {
            AutoDetectStep.Access -> AutoDetectPage.AccessHandoff
            AutoDetectStep.Background -> AutoDetectPage.BackgroundHandoff
            null -> AutoDetectPage.Why
        }

    /**
     * The page that follows once [step] is satisfied, or null when the flow is over.
     *
     * Null is what turns detection on: there is nothing left to ask for.
     */
    fun pageAfter(step: AutoDetectStep): AutoDetectPage? {
        val index = steps.indexOf(step)
        if (index < 0) return null
        return steps.getOrNull(index + 1)?.firstPage
    }

    /**
     * The system will not show the notifications dialog again, so the button has to open the
     * app's settings page instead of asking.
     */
    val notifyBlocked: Boolean get() = notifyStatus == PermissionStatus.Blocked

    /**
     * Whether the autostart reminder is worth showing in the settings body.
     *
     * Only while detection is on — it is advice about keeping a running feature running, and on
     * a screen where nothing is switched on it is a warning about nothing. And only until the
     * owner says they have handled it, because nothing can check for them.
     */
    val showAutostart: Boolean get() = needsAutostart && optedIn

    /**
     * Whether to warn that detection is switched on but cannot do anything.
     *
     * The state an owner lands in by turning the switch on and then backing out of the system
     * page. Nothing about the app looks wrong, and no fill will ever be detected.
     */
    val needsAccess: Boolean get() = optedIn && !accessGranted

    /**
     * Whether detection is on but Odo may not post the notification it produces.
     *
     * The same failure as [needsAccess] from the other end, and just as invisible: a fill is
     * detected, a draft is built, and it is dropped on the floor because nothing may show it.
     * Revoking notifications is something an owner does from the system's own UI, months later,
     * without any thought for this screen.
     */
    val needsNotifyPermission: Boolean get() = optedIn && !notifyStatus.isGranted
}
