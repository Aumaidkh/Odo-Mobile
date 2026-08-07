package com.hopcape.odo.core.triptracker

import com.hopcape.odo.core.triptracker.engine.TripTrackerEngine
import com.hopcape.odo.core.triptracker.observability.TripTrackerTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** [TripTracker]'s real implementation — [setEnabled] starts/stops [engine]; [status] mirrors it. */
internal class DefaultTripTracker(
    private val engine: TripTrackerEngine,
    private val telemetry: TripTrackerTelemetry,
) : TripTracker {

    init {
        engine.observeCar()
    }

    private val enabled = MutableStateFlow(false)

    override suspend fun setEnabled(enabled: Boolean) {
        if (this.enabled.value == enabled) return
        this.enabled.value = enabled
        engine.setEnabled(enabled)
        if (enabled) telemetry.enabled() else telemetry.disabled()
    }

    override val isEnabled: StateFlow<Boolean> get() = enabled

    override val status: Flow<TrackingStatus> get() = engine.status
}
