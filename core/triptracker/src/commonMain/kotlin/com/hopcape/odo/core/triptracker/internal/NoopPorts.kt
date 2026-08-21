package com.hopcape.odo.core.triptracker.internal

import com.hopcape.odo.core.triptracker.BluetoothRadio
import com.hopcape.odo.core.triptracker.BondedDeviceCatalog
import com.hopcape.odo.core.triptracker.BondedDevice
import com.hopcape.odo.core.triptracker.TrackingPreconditions
import com.hopcape.odo.core.triptracker.TrackingReadiness
import com.hopcape.odo.core.triptracker.VehicleBond
import com.hopcape.odo.core.triptracker.VehicleBondStore
import com.hopcape.odo.core.triptracker.model.FixRequest
import com.hopcape.odo.core.triptracker.model.LocationSample
import com.hopcape.odo.core.triptracker.model.MotionSignal
import com.hopcape.odo.core.triptracker.model.VehiclePresence
import com.hopcape.odo.core.triptracker.port.LocationProvider
import com.hopcape.odo.core.triptracker.port.MotionActivitySource
import com.hopcape.odo.core.triptracker.port.TripForegroundSession
import com.hopcape.odo.core.triptracker.port.VehiclePresenceSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/** Default stand-in for a signal source with no real adapter on this platform yet. */
internal class NoopLocationProvider : LocationProvider {
    override fun fixes(spec: FixRequest): Flow<LocationSample> = emptyFlow()
    override suspend fun lastKnown(): LocationSample? = null
}

internal class NoopMotionActivitySource : MotionActivitySource {
    override fun signals(): Flow<MotionSignal> = emptyFlow()
}

internal class NoopVehiclePresenceSource : VehiclePresenceSource {
    override fun presence(): Flow<VehiclePresence> = emptyFlow()
}

internal class NoopTripForegroundSession : TripForegroundSession {
    override fun start() = Unit
    override fun stop() = Unit
}

/** Every prerequisite reads false — a platform with no real adapter can never track. */
internal class NoopTrackingPreconditions : TrackingPreconditions {
    override fun status(): TrackingReadiness = TrackingReadiness(
        fineLocation = false,
        backgroundLocation = false,
        activityRecognition = false,
        bluetoothConnect = false,
        notifications = false,
    )
}

internal class NoopVehicleBondStore : VehicleBondStore {
    override suspend fun bond(): VehicleBond? = null
    override suspend fun saveBond(bond: VehicleBond) = Unit
    override suspend fun clearBond() = Unit
}

internal class NoopBondedDeviceCatalog : BondedDeviceCatalog {
    override suspend fun devices(): List<BondedDevice> = emptyList()
}

/**
 * Reads as on, not off. A platform with no real adapter has no radio to complain about, and
 * a stub that answered false would put a "your Bluetooth is off" card in front of an owner
 * with no way to act on it.
 */
internal class NoopBluetoothRadio : BluetoothRadio {
    override val enabled: Flow<Boolean> = flowOf(true)
}
