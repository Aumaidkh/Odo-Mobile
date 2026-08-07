package com.hopcape.odo.core.triptracker

import android.content.Context
import com.hopcape.odo.core.triptracker.bluetooth.AclVehiclePresenceSource
import com.hopcape.odo.core.triptracker.bluetooth.PrefsVehicleBondStore
import com.hopcape.odo.core.triptracker.location.FusedLocationProvider
import com.hopcape.odo.core.triptracker.motion.TransitionMotionSource
import com.hopcape.odo.core.triptracker.port.LocationProvider
import com.hopcape.odo.core.triptracker.port.MotionActivitySource
import com.hopcape.odo.core.triptracker.port.TripForegroundSession
import com.hopcape.odo.core.triptracker.port.VehiclePresenceSource
import com.hopcape.odo.core.triptracker.service.AndroidTripForegroundSession
import org.koin.dsl.module

/**
 * The real Android adapters (S8). [TransitionMotionSource], [AclVehiclePresenceSource] and
 * [PrefsVehicleBondStore] are bound as their concrete type too — the manifest-declared
 * `ActivityTransitionReceiver`/`BluetoothAclReceiver` resolve them by concrete type via
 * `KoinComponent`, since the OS instantiates receivers itself.
 */
val tripTrackerAndroidModule = module {
    single<LocationProvider> { FusedLocationProvider(context = get<Context>()) }

    single { TransitionMotionSource(context = get<Context>()) }
    single<MotionActivitySource> { get<TransitionMotionSource>() }

    single { AclVehiclePresenceSource() }
    single<VehiclePresenceSource> { get<AclVehiclePresenceSource>() }

    single<TripForegroundSession> { AndroidTripForegroundSession(context = get<Context>()) }
    single<TrackingPreconditions> { AndroidTrackingPreconditions(context = get<Context>()) }

    single { PrefsVehicleBondStore(context = get<Context>()) }
    single<VehicleBondStore> { get<PrefsVehicleBondStore>() }
}
