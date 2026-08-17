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

    override suspend fun setEnabled(enabled: Boolean) {
        if (this.enabled.value == enabled) return
        this.enabled.value = enabled
        engine.setEnabled(enabled)
        if (enabled) telemetry.enabled() else telemetry.disabled()
    }

    /**
     * Brings the engine up from the owner's stored intent, then checks whether the car is
     * connected *right now*.
     *
     * That second half is what makes arming work on a phone that never sees a connect event.
     * Presence otherwise arrives only as an ACL broadcast, so a process that starts while the
     * phone is already paired to the car — the app opened mid-drive, a cold start after the
     * process was killed, the owner returning from settings — armed into Standby and waited
     * for a broadcast that had already fired before the process existed. The next drive was
     * the earliest anything could be recorded.
     *
     * [TripTrackerEngine.startIfConnected] is the same call enrollment makes, and no-ops on
     * its own when there is no bond, the bond is GPS-only, or the stereo is not connected.
     */
    override suspend fun armFromPersistedState() {
        if (enabled.value) return
        vehicleBondStore.bond() ?: return
        val stored = settings.observe().first()
        if (!stored.trackerEnabled || stored.autoOdoPausedUntil != null) return
        setEnabled(true)
        engine.startIfConnected()
    }

    override val isEnabled: StateFlow<Boolean> get() = enabled

    override val status: Flow<TrackingStatus> get() = engine.status

    override suspend fun pauseActiveTrip() = engine.pauseActiveTrip()

    override suspend fun resumeActiveTrip() = engine.resumeActiveTrip()

    override suspend fun discardActiveTrip() = engine.discardActiveTrip()

    override suspend fun startIfConnected() = engine.startIfConnected()
}
