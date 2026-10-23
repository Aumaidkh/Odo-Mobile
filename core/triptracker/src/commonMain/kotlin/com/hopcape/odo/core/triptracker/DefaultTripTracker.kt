package com.hopcape.odo.core.triptracker

import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.triptracker.engine.TripTrackerEngine
import com.hopcape.odo.core.triptracker.observability.TripTrackerTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/** [TripTracker]'s real implementation — [setEnabled] starts/stops [engine]; [status] mirrors it. */
internal class DefaultTripTracker(
    private val engine: TripTrackerEngine,
    private val telemetry: TripTrackerTelemetry,
    private val vehicleBondStore: VehicleBondStore,
    private val settings: AppSettingsRepository,
) : TripTracker {

    init {
        engine.observeCar()
    }

    private val enabled = MutableStateFlow(false)

    /** The owner's own switch, so it is the only path that counts as turning tracking on or off. */
    override suspend fun setEnabled(enabled: Boolean) {
        if (!arm(enabled)) return
        if (enabled) telemetry.enabled() else telemetry.disabled()
    }

    /** Re-arms after a cold start or OS wake. Not an owner decision, so nothing is tracked. */
    override suspend fun armFromPersistedState() {
        if (enabled.value) return
        vehicleBondStore.bond() ?: return
        val stored = settings.observe().first()
        if (!stored.trackerEnabled || stored.autoOdoPausedUntil != null) return
        arm(true)
    }

    /**
     * Starts or stops the engine, answering false when nothing changed.
     *
     * Turning on also asks whether the car is already connected: presence is otherwise
     * event-only, so arming inside a connected car would wait a whole drive (#271).
     */
    private suspend fun arm(enabled: Boolean): Boolean {
        if (this.enabled.value == enabled) return false
        this.enabled.value = enabled
        engine.setEnabled(enabled)
        if (enabled) engine.startIfConnected()
        return true
    }

    override val isEnabled: StateFlow<Boolean> get() = enabled

    override val status: Flow<TrackingStatus> get() = engine.status

    override suspend fun pauseActiveTrip() = engine.pauseActiveTrip()

    override suspend fun resumeActiveTrip() = engine.resumeActiveTrip()

    override suspend fun discardActiveTrip() = engine.discardActiveTrip()

    override suspend fun startIfConnected() = engine.startIfConnected()
}
