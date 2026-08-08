package com.hopcape.odo.feature.autoodometer.domain.usecase

import com.hopcape.odo.core.triptracker.TripTracker

/**
 * Finishes the permission checklist (M4): turns tracking on, then starts the session
 * immediately if the bonded stereo is already connected.
 *
 * The immediate start is what lets the first drive work on while-using location alone
 * (plan §1) — enrollment finishes inside the running car, so there is no fresh Bluetooth
 * connect event left to wait for. [TripTracker.startIfConnected] no-ops on its own for
 * every other case (no bond, GPS-only bond, stereo not connected).
 */
internal class CompleteSetup(
    private val tracker: TripTracker,
) {
    suspend operator fun invoke() {
        tracker.setEnabled(true)
        tracker.startIfConnected()
    }
}
