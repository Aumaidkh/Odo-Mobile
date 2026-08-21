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

    /**
     * Turns the engine on or off, and on the way on asks Android what the car is doing right
     * now rather than only waiting to be told.
     *
     * Presence is otherwise event-only: `AclVehiclePresenceSource` carries nothing but ACL
     * connect/disconnect broadcasts. Arming while the phone is already connected therefore
     * used to mean waiting for a broadcast that had already fired — the owner got in the car,
     * the stereo connected, and only then did anything arm, so the next connect was a whole
     * drive away. That is issue #271, and it is the reason the seed lives here rather than at
     * each caller: every way of arming runs through this one method (cold start and OS wake
     * via [armFromPersistedState], finishing setup, resuming from a pause, the settings
     * toggle), and the bug was a call site that forgot. One place cannot forget.
     *
     * [TripTrackerEngine.startIfConnected] raises the same `PresenceConnected` a broadcast
     * would, so the speed gate still decides whether this is a drive — a car parked with the
     * stereo on does not become a trip. Firing it a second time when the real broadcast
     * arrives moments later costs nothing: the state machine treats a repeat in Standby as a
     * no-op.
     */
    override suspend fun setEnabled(enabled: Boolean) {
        if (this.enabled.value == enabled) return
        this.enabled.value = enabled
        engine.setEnabled(enabled)
        if (enabled) telemetry.enabled() else telemetry.disabled()
        if (enabled) engine.startIfConnected()
    }

    override suspend fun armFromPersistedState() {
        if (enabled.value) return
        vehicleBondStore.bond() ?: return
        val stored = settings.observe().first()
        if (!stored.trackerEnabled || stored.autoOdoPausedUntil != null) return
        setEnabled(true)
    }

    override suspend fun armFromPersistedState() {
        if (enabled.value) return
        vehicleBondStore.bond() ?: return
        val stored = settings.observe().first()
        if (!stored.trackerEnabled || stored.autoOdoPausedUntil != null) return
        setEnabled(true)
    }

    override val isEnabled: StateFlow<Boolean> get() = enabled

    override val status: Flow<TrackingStatus> get() = engine.status

    override suspend fun pauseActiveTrip() = engine.pauseActiveTrip()

    override suspend fun resumeActiveTrip() = engine.resumeActiveTrip()

    override suspend fun discardActiveTrip() = engine.discardActiveTrip()

    override suspend fun startIfConnected() = engine.startIfConnected()
}
