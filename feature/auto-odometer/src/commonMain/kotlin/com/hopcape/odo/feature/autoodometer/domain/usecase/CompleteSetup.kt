package com.hopcape.odo.feature.autoodometer.domain.usecase

import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.triptracker.TripTracker
import kotlinx.coroutines.flow.first

/**
 * Finishes the permission checklist (M4): persists the owner's intent, turns tracking on,
 * then starts the session immediately if the bonded stereo is already connected.
 *
 * The persisted `trackerEnabled` is what [TripTracker.armFromPersistedState] reads at cold
 * start — without it, the first process death after enrollment would silently turn the
 * feature off until the owner opened settings again. Persist failure is deliberately not
 * surfaced: the in-memory enable still holds for this session, and the next settings
 * write retries the same row.
 *
 * The immediate start is what lets the first drive work on while-using location alone
 * (plan §1) — enrollment finishes inside the running car, so there is no fresh Bluetooth
 * connect event left to wait for. [TripTracker.startIfConnected] no-ops on its own for
 * every other case (no bond, GPS-only bond, stereo not connected).
 */
internal class CompleteSetup(
    private val tracker: TripTracker,
    private val settings: AppSettingsRepository,
) {
    suspend operator fun invoke() {
        val current = settings.observe().first()
        settings.save(current.copy(trackerEnabled = true))
        tracker.setEnabled(true)
        // Looks redundant now that setEnabled seeds presence itself, and is not: setEnabled
        // returns early when the value has not changed, so re-running setup on a car whose
        // tracking was already on would seed nothing without this.
        tracker.startIfConnected()
    }
}
