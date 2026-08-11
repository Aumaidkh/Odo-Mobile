package com.hopcape.odo.core.triptracker

import com.hopcape.odo.core.triptracker.algorithm.CurvatureFactorRouteEstimator
import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import com.hopcape.odo.core.triptracker.engine.TripFinalizer
import com.hopcape.odo.core.triptracker.engine.TripTrackerEngine
import com.hopcape.odo.core.triptracker.observability.TripTrackerTelemetry
import com.hopcape.odo.core.triptracker.port.RouteDistanceEstimator
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
 * [RouteDistanceEstimator] is the one exception: it doesn't vary by platform (the real
 * curvature-factor estimator is commonMain), so its binding lives here from the start.
 * `TripSessionStore` is *not* bound here — its real implementation is
 * `:infrastructure:database`'s `TripSessionStoreImpl` (S6), registered by
 * `databaseInfrastructureModule`. A binding here would be later in `initKoin`'s module
 * list than that one and silently win, which is exactly the stale-stub trap this repo
 * has hit before — see `databaseInfrastructureModule`'s registration, not this file.
 */
val coreTripTrackerModule = module {
    single { TripTrackerConfig() }
    single { TripTrackerTelemetry(logger = get(), analytics = get(), tracer = get(), crash = get()) }

    single<RouteDistanceEstimator> { CurvatureFactorRouteEstimator(config = get()) }

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
            // "Keep trip routes" — read per trip, so turning it off applies to the drive
            // already underway rather than only the next one.
            settings = get(),
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
            vehicleBondStore = get(),
            bondedDeviceCatalog = get(),
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
