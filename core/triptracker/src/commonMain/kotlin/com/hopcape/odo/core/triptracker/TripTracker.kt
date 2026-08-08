package com.hopcape.odo.core.triptracker

import kotlinx.coroutines.flow.Flow

/** The one entry point other modules use. */
interface TripTracker {
    /** Turns automatic tracking on or off. Off = all signal sources released. */
    suspend fun setEnabled(enabled: Boolean)

    val isEnabled: Flow<Boolean>

    /** What the engine is doing right now, for the feature module's status UI. */
    val status: Flow<TrackingStatus>

    /** Soft-pauses the trip in progress without ending it (M5's Pause action). No-ops if nothing is tracking. */
    suspend fun pauseActiveTrip()

    /** Resumes a soft-paused trip (M5's Resume action). No-ops if nothing is soft-paused. */
    suspend fun resumeActiveTrip()

    /** Ends the trip in progress without recording it (M5's "Not driving" action). No-ops if nothing is tracking. */
    suspend fun discardActiveTrip()

    /**
     * Starts tracking immediately if the bonded stereo is already connected — for right
     * after enrollment finishes, so the first drive doesn't need a fresh Bluetooth
     * connect event (auto-odometer plan §1). No-ops if there is no bond, the bond isn't
     * [TriggerMode.STEREO], or the stereo isn't connected right now.
     */
    suspend fun startIfConnected()
}
