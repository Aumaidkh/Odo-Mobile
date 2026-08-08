package com.hopcape.odo.core.triptracker

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.model.Trip
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripStatus
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import com.hopcape.odo.core.triptracker.model.SessionSnapshot
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.common.id.UuidIdGenerator
import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import com.hopcape.odo.core.triptracker.engine.TripFinalizer
import com.hopcape.odo.core.triptracker.engine.TripTrackerEngine
import com.hopcape.odo.core.triptracker.internal.NoopLocationProvider
import com.hopcape.odo.core.triptracker.internal.NoopMotionActivitySource
import com.hopcape.odo.core.triptracker.internal.NoopTrackingPreconditions
import com.hopcape.odo.core.triptracker.internal.NoopTripForegroundSession
import com.hopcape.odo.core.triptracker.internal.NoopVehiclePresenceSource
import com.hopcape.odo.core.triptracker.observability.TripTrackerTelemetry
import com.hopcape.odo.core.triptracker.port.LocationProvider
import com.hopcape.odo.core.triptracker.port.MotionActivitySource
import com.hopcape.odo.core.triptracker.port.RouteDistanceEstimator
import com.hopcape.odo.core.triptracker.port.TripForegroundSession
import com.hopcape.odo.core.triptracker.port.TripSessionStore
import com.hopcape.odo.core.triptracker.port.VehiclePresenceSource
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Instant
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** Confirms the graph [coreTripTrackerModule] declares actually resolves end to end. */
class CoreTripTrackerModuleTest {

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun tripTracker_resolvesToDefaultTripTracker() {
        val koin = graph().koin
        assertIs<DefaultTripTracker>(koin.get<TripTracker>())
    }

    @Test
    fun everyBindingInTheModule_resolves() {
        val koin = graph().koin
        koin.get<TripTracker>()
        koin.get<TripTrackerConfig>()
        koin.get<TripTrackerTelemetry>()
        koin.get<RouteDistanceEstimator>()
        koin.get<TripSessionStore>()
        koin.get<TripFinalizer>()
        koin.get<TripTrackerEngine>()
    }

    /**
     * Stands in for the four observability modules, the platform module's
     * [TrackingPreconditions]/signal-source bindings, [TripSessionStore] (real impl in
     * `:infrastructure:database`'s `databaseInfrastructureModule`, deliberately not bound
     * by `coreTripTrackerModule` itself — see that module's KDoc), and the not-yet-built
     * [CarRepository]/[TripRepository] implementations, so [coreTripTrackerModule]
     * resolves the same way it will once the rest of the app graph is wired in.
     */
    private fun graph() = koinApplication {
        modules(
            module {
                single<Logger> { NoopLogger }
                single<AnalyticsTracker> { NoopAnalytics }
                single<PerformanceTracer> { NoopTracer }
                single<CrashRecorder> { NoopCrash }
                single<TrackingPreconditions> { NoopTrackingPreconditions() }
                single<LocationProvider> { NoopLocationProvider() }
                single<MotionActivitySource> { NoopMotionActivitySource() }
                single<VehiclePresenceSource> { NoopVehiclePresenceSource() }
                single<TripForegroundSession> { NoopTripForegroundSession() }
                single<TripSessionStore> { NoopTripSessionStore }
                single<CarRepository> { NoopCarRepository }
                single<TripRepository> { NoopTripRepository }
                single { UuidIdGenerator() }
                single<IdGenerator> { get<UuidIdGenerator>() }
            },
            coreTripTrackerModule,
        )
    }
}

private object NoopLogger : Logger {
    override fun log(
        level: LogLevel,
        tag: String,
        event: String,
        traceContext: TraceContext?,
        fields: Map<String, Any?>,
    ) = Unit

    override fun flush() = Unit
}

private object NoopAnalytics : AnalyticsTracker {
    override fun identify(traits: UserTraits) = Unit
    override fun track(eventName: String, properties: Map<String, Any?>) = Unit
    override fun setConsent(status: ConsentStatus) = Unit
    override fun flush() = Unit
}

private object NoopTracer : PerformanceTracer {
    override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
        object : Span {
            override val spanId = "span"
            override val traceId = traceId
            override val parentSpanId = parentSpanId
            override val name = name
            override fun setAttribute(key: String, value: Any?): Span = this
        }

    override fun endSpan(span: Span) = Unit
    override fun flush() = Unit
}

private object NoopCrash : CrashRecorder {
    override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) = Unit
    override fun leaveBreadcrumb(tag: String, message: String) = Unit
    override fun setCustomKey(key: String, value: Any?) = Unit
    override fun setUserId(userId: String?) = Unit
}

private object NoopCarRepository : CarRepository {
    override suspend fun add(car: Car) = throw NotImplementedError()
    override suspend fun update(car: Car) = throw NotImplementedError()
    override fun observePrimaryCar(): Flow<Car?> = flowOf(null)
    override fun observe(id: CarId): Flow<Car?> = flowOf(null)
    override suspend fun softDelete(id: CarId) = throw NotImplementedError()
}

private object NoopTripSessionStore : TripSessionStore {
    override suspend fun save(snapshot: SessionSnapshot) = Unit
    override suspend fun load(): SessionSnapshot? = null
    override suspend fun clear() = Unit
}

private object NoopTripRepository : TripRepository {
    override suspend fun add(trip: Trip): Nothing = throw NotImplementedError()
    override suspend fun addWithParked(trip: Trip, gap: Trip?, parked: ParkedLocation): Nothing = throw NotImplementedError()
    override fun observe(carId: CarId): Flow<List<Trip>> = flowOf(emptyList())
    override fun observeNeedingConfirmation(carId: CarId): Flow<List<Trip>> = flowOf(emptyList())
    override suspend fun setStatus(id: TripId, status: TripStatus): Nothing = throw NotImplementedError()
    override suspend fun countedSince(carId: CarId, after: Instant): List<Trip> = emptyList()
    override suspend fun parkedLocation(carId: CarId): ParkedLocation? = null
}
