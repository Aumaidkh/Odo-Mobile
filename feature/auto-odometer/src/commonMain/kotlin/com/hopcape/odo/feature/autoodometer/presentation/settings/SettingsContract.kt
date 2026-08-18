package com.hopcape.odo.feature.autoodometer.presentation.settings

import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.triptracker.TrackingReadiness
import com.hopcape.odo.core.triptracker.TriggerMode
import kotlinx.datetime.LocalDate

/**
 * One tracking precondition currently missing, relevant to the enrolled car's [TriggerMode]
 * (plan §5: "fine location / background location / activity recognition / bluetooth-connect,
 * whichever are false and relevant to the car's TriggerMode"). Notifications is left out on
 * purpose — `TrackingReadiness.canTrack` never reads it, so it never blocks tracking and never
 * earns a fix-it row (same reasoning [com.hopcape.odo.feature.autoodometer.presentation.permissions.PermissionSetupStep.NOTIFICATIONS]
 * is soft-required).
 */
internal enum class ReadinessIssue { FINE_LOCATION, BACKGROUND_LOCATION, ACTIVITY_RECOGNITION, BLUETOOTH_CONNECT }

/** Whether [readiness] holds this precondition. */
internal fun ReadinessIssue.isGranted(readiness: TrackingReadiness): Boolean = when (this) {
    ReadinessIssue.FINE_LOCATION -> readiness.fineLocation
    ReadinessIssue.BACKGROUND_LOCATION -> readiness.backgroundLocation
    ReadinessIssue.ACTIVITY_RECOGNITION -> readiness.activityRecognition
    ReadinessIssue.BLUETOOTH_CONNECT -> readiness.bluetoothConnect
}

/** Which [ReadinessIssue]s can even apply to [mode] — the engine never arms the other trigger's permission for this car. */
internal fun relevantReadinessIssues(mode: TriggerMode?): List<ReadinessIssue> = when (mode) {
    TriggerMode.STEREO -> listOf(ReadinessIssue.FINE_LOCATION, ReadinessIssue.BACKGROUND_LOCATION, ReadinessIssue.BLUETOOTH_CONNECT)
    TriggerMode.NO_STEREO -> listOf(ReadinessIssue.FINE_LOCATION, ReadinessIssue.BACKGROUND_LOCATION, ReadinessIssue.ACTIVITY_RECOGNITION)
    null -> emptyList()
}

/**
 * Display state for the settings screen (M7): the tracking toggle and its status line, the
 * trigger-device section, the monthly stats card, the privacy actions, and any lost-precondition
 * fix-it rows.
 *
 * [pausedUntil] non-null means the owner used "Pause for a week" and the pause has not expired
 * (an expired marker reads as not-paused — plan §4.1's `PauseTracking`/`ResumeTracking` split has
 * no scheduling job, so "expired" is only ever computed at read time). That state hides the
 * normal toggle behind an explicit "Resume tracking" action: a plain re-toggle would turn
 * tracking back on without clearing the stored pause marker, which is exactly what
 * [com.hopcape.odo.feature.autoodometer.domain.usecase.ResumeTracking] exists to do instead.
 */
internal data class SettingsUiState(
    val loading: Boolean = true,
    val trackingEnabled: Boolean = false,
    /** Null when tracking is off (and not paused) — nothing to say about status. */
    val statusDetail: UiText? = null,
    val mode: TriggerMode? = null,
    val hasBond: Boolean = false,
    val tripCount: Int = 0,
    val monthlyDistanceKm: Int = 0,
    val pausedUntil: LocalDate? = null,
    val readinessIssues: List<ReadinessIssue> = emptyList(),
    /**
     * Whether to show the autostart advice — this phone's brand holds background starts
     * behind a switch of its own and tracking is on, so Odo may be being blocked from
     * waking without anything here being wrong.
     *
     * Deliberately not a [ReadinessIssue]: those are read from `TrackingReadiness` and state
     * facts ("background location was turned off"). No API reads the manufacturer's switch,
     * so this can only be phrased as a possibility, and phrasing it as a fact would be a
     * claim Odo cannot support.
     */
    val showAutostartAdvice: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val deleting: Boolean = false,
) {
    val isPaused: Boolean get() = pausedUntil != null
}

/** What the owner did on the settings screen, as data. */
internal sealed interface SettingsEvent {

    /** The tracking toggle — a direct mirror of `TripTracker.isEnabled`, not a paused-state action. */
    data object ToggleTapped : SettingsEvent

    /** "Resume tracking" — the explicit way out of a "Pause for a week". */
    data object ResumeTapped : SettingsEvent

    /** "Change" on the trigger-device row — re-enters the device picker (F5). */
    data object ChangeDeviceTapped : SettingsEvent

    /** "Pause for a week". */
    data object PauseWeekTapped : SettingsEvent

    /** The autostart advice's action — opens the manufacturer's own page. */
    data object AutostartAdviceTapped : SettingsEvent

    /** "Delete all trip data" — opens the confirm dialog. */
    data object DeleteTapped : SettingsEvent

    /** The confirm dialog's destructive action. */
    data object DeleteConfirmed : SettingsEvent

    /** The confirm dialog's cancel, or a tap outside it. */
    data object DeleteDismissed : SettingsEvent

    /** Back arrow. */
    data object BackTapped : SettingsEvent

    /**
     * A relevant tracking-permission controller's status, as read at the route host — on first
     * composition (screen entry) and again whenever the system dialog or the settings page
     * answers, the same reactive plumbing [com.hopcape.odo.feature.autoodometer.presentation.permissions.PermissionSetupEvent.StatusObserved]
     * uses (plan §5's "re-checked on every settings-screen entry and app foreground").
     */
    data class ReadinessChanged(val readiness: TrackingReadiness) : SettingsEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface SettingsEffect {
    data object NavigateBack : SettingsEffect
    data object NavigateToDevicePicker : SettingsEffect
}
