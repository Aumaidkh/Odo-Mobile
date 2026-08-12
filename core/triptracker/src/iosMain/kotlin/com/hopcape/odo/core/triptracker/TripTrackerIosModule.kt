package com.hopcape.odo.core.triptracker

import com.hopcape.odo.core.triptracker.internal.NoopBondedDeviceCatalog
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

/** MVP is Android-only (CLAUDE.md) — every port stays a Noop on iOS. */
val tripTrackerIosModule = module {
    single<LocationProvider> { NoopLocationProvider() }
    single<MotionActivitySource> { NoopMotionActivitySource() }
    single<VehiclePresenceSource> { NoopVehiclePresenceSource() }
    single<TripForegroundSession> { NoopTripForegroundSession() }
    single<TrackingPreconditions> { NoopTrackingPreconditions() }
    single<VehicleBondStore> { NoopVehicleBondStore() }
    single<BondedDeviceCatalog> { NoopBondedDeviceCatalog() }
}
