package com.hopcape.odo.feature.autoodometer.presentation.triplogged

import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.platform.permission.PermissionStatus

/**
 * Display state for the trip-logged screen (M6): the odometer drum, the trip's stats, the
 * optional service-due nudge, and the one-time background-location upgrade card.
 *
 * Distances are kept as raw kilometres rather than a display-unit `Long` — the ViewModel
 * stays Compose-free; the screen converts with `LocalOdoDistanceFormat` at render time, the
 * same convention every other feature's state follows.
 */
internal data class TripLoggedUiState(
    val loading: Boolean = true,
    /** The drum's current reading, or `null` before the first load or when there is no manual-reading anchor to derive from. */
    val odometerNowKm: Int? = null,
    /** What the drum read right before this trip — the "was %1$s before this drive" line. */
    val odometerBeforeKm: Int? = null,
    /** `odometerNowKm - odometerBeforeKm` — both the headline "N km added" and the Distance stat row, kept as one number so the two never disagree. */
    val addedKm: Int? = null,
    val duration: UiText? = null,
    /** `null` hides the "SERVICE DUE" section — nothing due soon is not worth a nudge. */
    val nudge: UiText? = null,
    val showRejectConfirm: Boolean = false,
    val acknowledging: Boolean = false,
    val backgroundUpgrade: BackgroundUpgradeCardState = BackgroundUpgradeCardState(),
)

/**
 * The plan §5 step-5 upgrade card: "you've seen it work — make it automatic". Shown exactly
 * until [granted] — an explicit [dismissed] flag (the card's own "Not now") does not count
 * as granted, so the card returns on the next pending trip rather than being seen once and
 * silently opted out forever.
 */
internal data class BackgroundUpgradeCardState(
    val granted: Boolean = false,
    val dismissed: Boolean = false,
    val askedOnce: Boolean = false,
) {
    val visible: Boolean get() = !granted && !dismissed
}

/** What the owner did on the trip-logged screen, as data. */
internal sealed interface TripLoggedEvent {

    /** "Done" — acknowledges the trip and pops back to wherever the redirect came from. */
    data object DoneTapped : TripLoggedEvent

    /** "That wasn't me driving" — opens the confirm dialog. */
    data object RejectTapped : TripLoggedEvent

    /** The confirm dialog's destructive action. */
    data object RejectConfirmed : TripLoggedEvent

    /** The confirm dialog's cancel, or a tap outside it. */
    data object RejectDismissed : TripLoggedEvent

    /** The background-location upgrade card's primary CTA. */
    data object UpgradeTapped : TripLoggedEvent

    /** The upgrade card's "Not now". */
    data object UpgradeDismissed : TripLoggedEvent

    /**
     * The `ACCESS_BACKGROUND_LOCATION` controller's status, as read at the route host — on
     * first composition (so an already-granted permission hides the card without a tap) and
     * again after the system settings page answers, mirroring
     * [com.hopcape.odo.feature.autoodometer.presentation.permissions.PermissionSetupEvent.StatusObserved].
     */
    data class BackgroundLocationStatusObserved(val status: PermissionStatus) : TripLoggedEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface TripLoggedEffect {
    /** Pops back to whichever top-level tab the app-shell redirect fired from. */
    data object NavigateBack : TripLoggedEffect
}
