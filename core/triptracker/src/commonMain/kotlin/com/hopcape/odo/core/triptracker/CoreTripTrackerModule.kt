package com.hopcape.odo.core.triptracker

import com.hopcape.odo.core.triptracker.algorithm.CurvatureFactorRouteEstimator
import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import com.hopcape.odo.core.triptracker.engine.TripFinalizer
import com.hopcape.odo.core.triptracker.engine.TripTrackerEngine
import com.hopcape.odo.core.triptracker.internal.NoopTripSessionStore
import com.hopcape.odo.core.triptracker.observability.TripTrackerTelemetry
import com.hopcape.odo.core.triptracker.port.RouteDistanceEstimator
import com.hopcape.odo.core.triptracker.port.TripSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Clock
import org.koin.dsl.module

/**
 * The common bindings — platform port bindings ([tripTrackerAndroidModule] /
 * [tripTrackerIosModule]) live only in the platform modules, never here, so there is no
 * later-module-wins override game between them.
 *
 * [RouteDistanceEstimator] and [TripSessionStore] are the two exceptions: neither varies
 * by platform (the real curvature-factor estimator is commonMain, and the real session
 * store is the same `:infrastructure:database` impl on both platforms), so their bindings
 * live here from the start. [RouteDistanceEstimator] is real as of S3;
 * [TripSessionStore] stays a Noop until `:infrastructure:database` supplies the real
 * journal (S6) — until then a process kill during a trip loses that trip's distance
 * instead of resuming it, same as before any of this module existed.
 */
val coreTripTrackerModule = module {
    single { TripTrackerConfig() }
    single { TripTrackerTelemetry(logger = get(), analytics = get(), tracer = get(), crash = get()) }

    single<RouteDistanceEstimator> { CurvatureFactorRouteEstimator(config = get()) }
    single<TripSessionStore> { NoopTripSessionStore() }

    // SupervisorJob: one signal source's collector failing must not cancel the others or
    // the timer jobs. Dispatchers.Default: no UI work happens here.
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single {
        TripFinalizer(
            ids = get(),
            tripRepository = get(),
            routeEstimator = get(),
            config = get(),
            telemetry = get(),
        )
    }

    single {
        TripTrackerEngine(
            locationProvider = get(),
            motionSource = get(),
            presenceSource = get(),
            foregroundSession = get(),
            sessionStore = get(),
            preconditions = get(),
            carRepository = get(),
            tripRepository = get(),
            finalizer = get(),
            telemetry = get(),
            config = get(),
            scope = get(),
            now = { Clock.System.now() },
        )
    }

    single { DefaultTripTracker(engine = get(), telemetry = get()) }
    single<TripTracker> { get<DefaultTripTracker>() }
}
