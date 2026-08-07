package com.hopcape.odo.core.triptracker

import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import com.hopcape.odo.core.triptracker.internal.NoopRouteDistanceEstimator
import com.hopcape.odo.core.triptracker.internal.NoopTripSessionStore
import com.hopcape.odo.core.triptracker.observability.TripTrackerTelemetry
import com.hopcape.odo.core.triptracker.port.RouteDistanceEstimator
import com.hopcape.odo.core.triptracker.port.TripSessionStore
import org.koin.dsl.module

/**
 * The common bindings — platform port bindings ([tripTrackerAndroidModule] /
 * [tripTrackerIosModule]) live only in the platform modules, never here, so there is no
 * later-module-wins override game between them.
 *
 * [RouteDistanceEstimator] and [TripSessionStore] are the two exceptions: neither varies
 * by platform (the real curvature-factor estimator is commonMain, and the real session
 * store is the same `:infrastructure:database` impl on both platforms), so their bindings
 * live here from the start — a Noop today, swapped for the real class in S3/S6.
 */
val coreTripTrackerModule = module {
    single { TripTrackerConfig() }
    single { TripTrackerTelemetry(logger = get(), analytics = get(), tracer = get(), crash = get()) }

    single<RouteDistanceEstimator> { NoopRouteDistanceEstimator() }
    single<TripSessionStore> { NoopTripSessionStore() }

    single { DefaultTripTracker(preconditions = get()) }
    single<TripTracker> { get<DefaultTripTracker>() }
}
