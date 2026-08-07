package com.hopcape.odo.core.triptracker

import kotlinx.coroutines.flow.Flow

/** The one entry point other modules use. */
interface TripTracker {
    /** Turns automatic tracking on or off. Off = all signal sources released. */
    suspend fun setEnabled(enabled: Boolean)

    val isEnabled: Flow<Boolean>

    /** What the engine is doing right now, for the feature module's status UI. */
    val status: Flow<TrackingStatus>
}
