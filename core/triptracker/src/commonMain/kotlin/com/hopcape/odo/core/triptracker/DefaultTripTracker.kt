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
