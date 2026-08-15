package com.hopcape.odo.feature.refuel.presentation.autodetect

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.refuel.DetectionApp
import com.hopcape.odo.core.domain.refuel.IgnoredMerchant
import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.platform.permission.isGranted

/**
 * What still has to happen before auto-detect can do anything, in the order it is asked for.
 *
 * Two separate Android permissions are involved and they are easy to mistake for one. The
 * first lets Odo *post* a notification; the second lets Odo *read* the one a payment app
 * posted. Granting the first does nothing for detection on its own, so the screen asks for
 * them one at a time and names each ask for what it does.
 */
internal enum class AutoDetectSetupStep {

    /** `POST_NOTIFICATIONS` — without it a detected fill has nowhere to appear. */
    PostNotifications,

    /** Notification access — the listener permission detection actually runs on. */
    NotificationAccess,

    /** Both are held; the next tap is the only one that turns Odo's own switch on. */
    Ready,
}

/**
 * The opt-in reads as two screens rather than one.
 *
 * It used to be a single scroll: the pitch, four privacy promises, an on-device note, a
 * three-step checklist, a warning about the OEM permission screen and two buttons. Every part
 * earned its place and together they were more than anyone reads, which on a screen asking for
 * a sensitive permission is the opposite of informed consent — a wall of text gets skipped,
 * and skipped text persuades nobody.
 *
 * Split by question instead: what this does, then what it needs.
 */
internal enum class AutoDetectOptInPage {

    /** What detection is for, what it reads, and what it never touches. */
    Why,

    /** The two Android notification permissions, and the OEM warning about the second. */
    Permissions,

    /**
     * The manufacturer's background-start switch, on the phones that have one.
     *
     * Its own page because it is a different kind of thing from the two before it. Those are
     * Android permissions Odo can ask for and then read back; this is an OEM setting buried
     * in a menu, which no API can request and no API can check. Mixing them put an
     * unverifiable step in a checklist of verifiable ones and made the whole list feel
     * approximate.
     */
    Autostart,
}

/** What the owner did on the auto-detect screen. */
internal sealed interface AutoDetectEvent {

    /** The master switch in the settings body, and the last tap of the opt-in. */
    data class DetectionToggled(val enabled: Boolean) : AutoDetectEvent

    /**
     * `POST_NOTIFICATIONS` as the route host reads it.
     *
     * Fires on first composition, so a phone that already granted it skips step one without
     * a tap, and again every time the system dialog or the settings page answers.
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
     * The opt-in's primary button, whatever it currently says.
     *
     * Carried as one event rather than three because the button is one thing to the owner —
     * "get me to the next step". Which step that is comes from
     * [AutoDetectUiState.setupStep], and the route host is what performs it, because two of
     * the three need a permission controller that only a composable can hold.
     */
    data object SetupContinued : AutoDetectEvent

    /** "See what it needs" — move from the pitch to the permissions. */
    data object OptInAdvanced : AutoDetectEvent

    /** Back, while on the second opt-in page: return to the first rather than leaving. */
    data object OptInBacked : AutoDetectEvent

    /** Take me to this phone's own autostart page. */
    data object OpenAutostartSettings : AutoDetectEvent

    /** "Done" on the autostart advice — the owner says they have handled it. */
    data object AutostartAcknowledged : AutoDetectEvent

    /** The screen came back to the foreground — re-read the permission. */
    data object Resumed : AutoDetectEvent

    data object NotNowTapped : AutoDetectEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface AutoDetectEffect {

    data object Dismiss : AutoDetectEffect
}

/**
 * The auto-detect screen's state.
 *
 * One screen serves two jobs, and [optedIn] is what switches between them. Before the owner
 * has turned anything on it is the explanation — what would be read, what would not, and that
 * none of it leaves the phone. Afterwards it is the settings, including the way back out.
 *
 * [accessGranted] is read from the OS, not from Odo's own switch, because the two can
 * disagree: the owner can revoke notification access in system settings at any time, and a
 * screen showing "on" while nothing is being read would be lying.
 */
@Immutable
internal data class AutoDetectUiState(
    val loading: Boolean = true,
    val optedIn: Boolean = false,
    val accessGranted: Boolean = false,
    /**
     * `POST_NOTIFICATIONS`, read from the OS at the route host.
     *
     * Separate from [accessGranted] because they are separate permissions with separate
     * failure modes: this one has a system dialog Odo can trigger, the other only has a
     * settings page Odo can open.
     */
    val notifyStatus: PermissionStatus = PermissionStatus.Askable,
    val confirmBeforeLog: Boolean = true,
    val predictOdometer: Boolean = true,
    val apps: List<DetectionApp> = emptyList(),
    val ignoredMerchants: List<IgnoredMerchant> = emptyList(),
    val detectedFillCount: Int = 0,
    /**
     * Whether this phone holds background starts behind a switch of its own.
     *
     * Shown even when everything else is granted and working, because it is the difference
     * between detection that works today and detection that works after the phone next
     * reclaims the app. Nothing in the app can set it.
     */
    val needsAutostart: Boolean = false,
    /** Which half of the opt-in is on screen. Ignored once [optedIn]. */
    val optInPage: AutoDetectOptInPage = AutoDetectOptInPage.Why,
) {
    /**
     * Whether the autostart advice is worth showing right now.
     *
     * Only while detection is on — it is advice about keeping a running feature running, and
     * on a screen where nothing is switched on it is a warning about nothing. And only until
     * the owner says they have handled it, because nothing can check for them.
     */
    val showAutostart: Boolean get() = needsAutostart && optedIn

    /**
     * Which ask the opt-in's primary button performs next.
     *
     * Deliberately in this order. Notification access is the one the owner is most likely to
     * back out of — its system dialog warns that Odo will be able to read all notifications —
     * and reaching it having already granted the smaller permission means the screen can say
     * plainly why the first one was not enough.
     *
     * Background start is not part of this chain. No API reports whether it is on, so a step
     * that gated the button on it could never clear, and detection does work until the phone
     * next reclaims the app. It is advice on this screen, not a gate.
     */
    val setupStep: AutoDetectSetupStep
        get() = when {
            !notifyStatus.isGranted -> AutoDetectSetupStep.PostNotifications
            !accessGranted -> AutoDetectSetupStep.NotificationAccess
            else -> AutoDetectSetupStep.Ready
        }

    /**
     * Whether the permissions page still has an ask left in it.
     *
     * False once both are granted, which is when the flow moves on — to the autostart page on
     * a phone that needs one, and straight to switching detection on everywhere else.
     */
    val permissionsSettled: Boolean get() = setupStep == AutoDetectSetupStep.Ready

    /**
     * The system will not show the notifications dialog again, so the button has to go to the
     * app's settings page instead of asking.
     */
    val notifyBlocked: Boolean get() = notifyStatus == PermissionStatus.Blocked

    /**
     * Whether to warn that detection is switched on but cannot do anything.
     *
     * The state an owner lands in by turning the switch on and then backing out of the
     * system page. Nothing about the app looks wrong, and no fill will ever be detected.
     */
    val needsAccess: Boolean get() = optedIn && !accessGranted

    /**
     * Whether detection is on but Odo may not post the notification it produces.
     *
     * The same failure as [needsAccess] from the other end, and just as invisible: a fill is
     * detected, a draft is built, and it is dropped on the floor because nothing may show it.
     * Revoking notifications is something an owner does from the system's own UI, months
     * later, without any thought for this screen.
     */
    val needsNotifyPermission: Boolean get() = optedIn && !notifyStatus.isGranted
}
