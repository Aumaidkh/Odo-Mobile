package com.hopcape.odo.core.triptracker.internal

import com.hopcape.odo.core.domain.trip.model.GeoPoint
import com.hopcape.odo.core.domain.trip.model.TripDistance
import com.hopcape.odo.core.triptracker.TrackingPreconditions
import com.hopcape.odo.core.triptracker.TrackingReadiness
import com.hopcape.odo.core.triptracker.VehicleBond
import com.hopcape.odo.core.triptracker.VehicleBondStore
import com.hopcape.odo.core.triptracker.model.FixRequest
import com.hopcape.odo.core.triptracker.model.LocationSample
import com.hopcape.odo.core.triptracker.model.MotionSignal
import com.hopcape.odo.core.triptracker.model.SessionSnapshot
import com.hopcape.odo.core.triptracker.model.VehiclePresence
import com.hopcape.odo.core.triptracker.port.LocationProvider
import com.hopcape.odo.core.triptracker.port.MotionActivitySource
import com.hopcape.odo.core.triptracker.port.RouteDistanceEstimator
import com.hopcape.odo.core.triptracker.port.TripForegroundSession
import com.hopcape.odo.core.triptracker.port.TripSessionStore
import com.hopcape.odo.core.triptracker.port.VehiclePresenceSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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

/** Stand-in until :infrastructure:database supplies the real journal (S6). */
internal class NoopTripSessionStore : TripSessionStore {
    override suspend fun save(snapshot: SessionSnapshot) = Unit
    override suspend fun load(): SessionSnapshot? = null
    override suspend fun clear() = Unit
}

/** Stand-in until the curvature-factor estimator lands (S3). */
internal class NoopRouteDistanceEstimator : RouteDistanceEstimator {
    override suspend fun estimate(from: GeoPoint, to: GeoPoint): TripDistance? = null
}
