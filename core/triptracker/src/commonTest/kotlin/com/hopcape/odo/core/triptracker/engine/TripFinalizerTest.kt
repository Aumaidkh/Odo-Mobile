package com.hopcape.odo.core.triptracker.engine

import arrow.core.Either
import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.model.PrivacyPreferences
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.model.Trip
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.odo.core.domain.trip.model.TripStatus
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import com.hopcape.odo.core.triptracker.algorithm.CurvatureFactorRouteEstimator
import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import com.hopcape.odo.core.triptracker.model.LocationSample
import com.hopcape.odo.core.triptracker.observability.TripTrackerTelemetry
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The "Keep trip routes" gate, at the one place coordinates are written.
 *
 * The distance assertions are the point of the off-path tests: the switch must cost the
 * owner their route and nothing else, and the distance is integrated during the drive rather
 * than recomputed from the points being dropped.
 */
class TripFinalizerTest {

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")
    private val startedAt = Instant.parse("2026-08-11T09:00:00Z")
    private val endedAt = Instant.parse("2026-08-11T09:20:00Z")

    private fun session() = TripSession(
        startedAt = startedAt,
        mode = TripMode.BT_VERIFIED,
        distanceMeters = 12_000,
        estimatedMeters = 12_000,
        firstFix = LocationSample(at = startedAt, elapsed = 0.seconds, lat = 18.52, lon = 73.85, accuracyM = 5f),
        lastGoodFix = LocationSample(at = endedAt, elapsed = 1_200.seconds, lat = 18.60, lon = 73.90, accuracyM = 5f),
        attributionConfident = true,
    )

    private fun finalizer(
        trips: TripRepository,
        keepRoutes: Boolean,
    ): TripFinalizer {
        val config = TripTrackerConfig()
        return TripFinalizer(
            ids = SequentialIds,
            tripRepository = trips,
            routeEstimator = CurvatureFactorRouteEstimator(config),
            config = config,
            telemetry = TripTrackerTelemetry(
                logger = SilentLogger,
                analytics = SilentAnalytics,
                tracer = SilentTracer,
                crash = SilentCrash,
            ),
            settings = RoutesSetting(keepRoutes),
        )
    }

    @Test
    fun routesOn_storesTheCoordinatesAndTheParkedLocation() = runTest {
        val trips = RecordingTrips()

        finalizer(trips, keepRoutes = true).finalize(session(), endedAt, carId, ownerId, parked = null)

        val trip = trips.added.single()
        assertNotNull(trip.startPoint)
        assertNotNull(trip.endPoint)
        assertNotNull(trips.parked, "the end point is what anchors the next trip's attribution")
    }

    @Test
    fun routesOff_storesTheTripWithNoCoordinates() = runTest {
        val trips = RecordingTrips()

        finalizer(trips, keepRoutes = false).finalize(session(), endedAt, carId, ownerId, parked = null)

        val trip = trips.added.single()
        assertNull(trip.startPoint)
        assertNull(trip.endPoint)
    }

    @Test
    fun routesOff_keepsTheDistance() = runTest {
        val trips = RecordingTrips()

        finalizer(trips, keepRoutes = false).finalize(session(), endedAt, carId, ownerId, parked = null)

        // The whole reason the trip is still worth saving. Distance is integrated during the
        // drive, not derived from the points that were just dropped.
        assertEquals(12_000, trips.added.single().distance.meters)
    }

    @Test
    fun routesOff_doesNotRememberWhereTheCarWasLeft() = runTest {
        val trips = RecordingTrips()

        finalizer(trips, keepRoutes = false).finalize(session(), endedAt, carId, ownerId, parked = null)

        // A parked location is a coordinate the app kept, which is exactly what the switch
        // is about. The cost — GPS-only trips lose their attribution anchor — is the trade.
        assertNull(trips.parked)
        assertTrue(trips.addedWithoutParked, "the plain add() path, not addWithParked()")
    }

    @Test
    fun routesOff_writesNoGapInferredTwin() = runTest {
        val trips = RecordingTrips()
        val parked = ParkedLocation(
            carId,
            com.hopcape.odo.core.domain.trip.model.GeoPoint.of(18.40, 73.70).getOrNull()!!,
            startedAt,
        )

        finalizer(trips, keepRoutes = false).finalize(session(), endedAt, carId, ownerId, parked = parked)

        // The twin exists to describe the stretch between two coordinates. With routes off
        // there is no start point to measure from, so it must not be invented.
        assertEquals(1, trips.added.size)
        assertTrue(trips.added.none { it.mode == TripMode.GAP_INFERRED })
    }
}

private class RecordingTrips : TripRepository {
    val added = mutableListOf<Trip>()
    var parked: ParkedLocation? = null
        private set
    var addedWithoutParked = false
        private set

    override suspend fun add(trip: Trip): Either<DomainError, Trip> {
        added += trip
        addedWithoutParked = true
        return trip.right()
    }

    override suspend fun addWithParked(trip: Trip, gap: Trip?, parked: ParkedLocation): Either<DomainError, Trip> {
        added += trip
        gap?.let { added += it }
        this.parked = parked
        return trip.right()
    }

    override fun observe(carId: CarId): Flow<List<Trip>> = flowOf(added)
    override fun observeNeedingConfirmation(carId: CarId): Flow<List<Trip>> = flowOf(emptyList())
    override suspend fun setStatus(id: TripId, status: TripStatus): Either<DomainError, Unit> = Unit.right()
    override suspend fun countedSince(carId: CarId, after: Instant): List<Trip> = emptyList()
    override suspend fun countedBetween(carId: CarId, from: Instant, to: Instant): List<Trip> = emptyList()
    override suspend fun parkedLocation(carId: CarId): ParkedLocation? = parked
    override suspend fun deleteAllForCar(carId: CarId): Either<DomainError, Unit> = Unit.right()
    override suspend fun forgetRoutes(): Either<DomainError, Unit> = Unit.right()
}

private class RoutesSetting(keepTripRoutes: Boolean) : AppSettingsRepository {
    private val stored = MutableStateFlow(
        AppSettings.Default.copy(privacy = PrivacyPreferences(keepTripRoutes = keepTripRoutes)),
    )
    override fun observe(): Flow<AppSettings> = stored
    override suspend fun save(settings: AppSettings): Either<DomainError, AppSettings> {
        stored.value = settings
        return settings.right()
    }
}

private object SequentialIds : IdGenerator {
    private var counter = 0
    override fun newId(): String = "trip-${counter++}"
}

private object SilentLogger : Logger {
    override fun log(level: LogLevel, tag: String, event: String, traceContext: TraceContext?, fields: Map<String, Any?>) = Unit
    override fun flush() = Unit
}

private object SilentAnalytics : AnalyticsTracker {
    override fun identify(traits: UserTraits) = Unit
    override fun track(eventName: String, properties: Map<String, Any?>) = Unit
    override fun setConsent(status: ConsentStatus) = Unit
    override fun flush() = Unit
}

private object SilentTracer : PerformanceTracer {
    override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span = object : Span {
        override val spanId = "span"
        override val traceId = traceId
        override val parentSpanId = parentSpanId
        override val name = name
        override fun setAttribute(key: String, value: Any?): Span = this
    }
    override fun endSpan(span: Span) = Unit
    override fun flush() = Unit
}

private object SilentCrash : CrashRecorder {
    override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) = Unit
    override fun leaveBreadcrumb(tag: String, message: String) = Unit
    override fun setCustomKey(key: String, value: Any?) = Unit
    override fun setUserId(userId: String?) = Unit
}
