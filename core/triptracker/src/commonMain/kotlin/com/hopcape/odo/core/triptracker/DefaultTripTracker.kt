package com.hopcape.odo.core.triptracker

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * [TripTracker] stub: tracks the enabled flag in memory and reports [TrackingStatus.Disabled]
 * or [TrackingStatus.Standby] — there is no engine yet to ever report [TrackingStatus.Tracking].
 * S5 replaces this with the real, engine-backed implementation.
 */
internal class DefaultTripTracker(
    private val preconditions: TrackingPreconditions,
) : TripTracker {

    private val enabled = MutableStateFlow(false)

    override suspend fun setEnabled(enabled: Boolean) {
        this.enabled.value = enabled
    }

    override val isEnabled: StateFlow<Boolean> get() = enabled

    override val status: Flow<TrackingStatus> = enabled.map { isEnabled ->
        if (isEnabled) TrackingStatus.Standby(preconditions.status()) else TrackingStatus.Disabled
    }
}
