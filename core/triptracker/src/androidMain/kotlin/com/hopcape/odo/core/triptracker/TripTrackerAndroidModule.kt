package com.hopcape.odo.core.triptracker

import com.hopcape.odo.core.triptracker.internal.NoopLocationProvider
import com.hopcape.odo.core.triptracker.internal.NoopMotionActivitySource
import com.hopcape.odo.core.triptracker.internal.NoopTrackingPreconditions
import com.hopcape.odo.core.triptracker.internal.NoopTripForegroundSession
import com.hopcape.odo.core.triptracker.internal.NoopVehicleBondStore
import com.hopcape.odo.core.triptracker.internal.NoopVehiclePresenceSource
import com.hopcape.odo.core.triptracker.port.LocationProvider
import com.hopcape.odo.core.triptracker.port.MotionActivitySource
import com.hopcape.odo.core.triptracker.port.TripForegroundSession
import com.hopcape.odo.core.triptracker.port.VehiclePresenceSource
import org.koin.dsl.module

/** Noop bindings for every platform-shaped port. S8 replaces each with a real adapter. */
val tripTrackerAndroidModule = module {
    single<LocationProvider> { NoopLocationProvider() }
    single<MotionActivitySource> { NoopMotionActivitySource() }
    single<VehiclePresenceSource> { NoopVehiclePresenceSource() }
    single<TripForegroundSession> { NoopTripForegroundSession() }
    single<TrackingPreconditions> { NoopTrackingPreconditions() }
    single<VehicleBondStore> { NoopVehicleBondStore() }
}
