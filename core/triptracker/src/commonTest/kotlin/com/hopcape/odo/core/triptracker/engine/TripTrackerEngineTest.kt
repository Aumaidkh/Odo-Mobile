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
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.trip.model.GeoPoint
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.model.Trip
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.odo.core.domain.trip.model.TripStatus
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import com.hopcape.odo.core.triptracker.TrackingPreconditions
import com.hopcape.odo.core.triptracker.TrackingReadiness
import com.hopcape.odo.core.triptracker.algorithm.CurvatureFactorRouteEstimator
import com.hopcape.odo.core.triptracker.algorithm.TraceBuilder
import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import com.hopcape.odo.core.triptracker.model.LocationSample
import com.hopcape.odo.core.triptracker.model.MotionKind
import com.hopcape.odo.core.triptracker.model.MotionSignal
import com.hopcape.odo.core.triptracker.model.SessionSnapshot
import com.hopcape.odo.core.triptracker.model.VehiclePresence
import com.hopcape.odo.core.triptracker.observability.TripTrackerTelemetry
import com.hopcape.odo.core.triptracker.port.LocationProvider
import com.hopcape.odo.core.triptracker.port.MotionActivitySource
import com.hopcape.odo.core.triptracker.port.TripForegroundSession
import com.hopcape.odo.core.triptracker.port.TripSessionStore
import com.hopcape.odo.core.triptracker.port.VehiclePresenceSource
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TripTrackerEngineTest {

    private val config = TripTrackerConfig()
    private val baseInstant = Instant.fromEpochSeconds(1_700_000_000)
    private val car = Car.create(
        id = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        make = "Maruti",
        model = "Swift",
        year = 2020,
        fuelType = FuelType.PETROL,
        odometerKm = 10_000,
    ).getOrNull()!!

    @Test
    fun btDrive_savesATripWithTheCorrectDistance() = runTest {
        val env = Env(this, parked = null)
        env.enableAndSettleCar()

        env.driveBtFrom(0)
        val trip = env.stopBtAndFinalize()

        assertEquals(TripMode.BT_VERIFIED, trip.mode)
        assertEquals(1_170L, trip.distance.meters)
        assertEquals(TripStatus.RECORDED, trip.status)
    }

    @Test
    fun petrolPumpStop_stitchesInsteadOfEndingTheTrip() = runTest {
        val env = Env(this, parked = null)
        env.enableAndSettleCar()

        env.driveBtFrom(0)

        // Stop signal, but the owner is back before the stitch window closes.
        env.settleMotion(MotionKind.STILL)
        env.presence.emit(VehiclePresence.Disconnected)
        env.advance()
        advanceTimeBy(1.seconds)
        env.presence.emit(VehiclePresence.Connected("aa:bb"))
        env.advance()

        // Drive a bit more, then stop for real.
        env.driveBtFrom(40)
        val trip = env.stopBtAndFinalize()

        assertEquals(1, env.tripRepository.added.size)
        assertTrue(trip.distance.meters > 1_170L, "expected the post-stitch distance included, got ${trip.distance.meters}")
    }

    @Test
    fun killAndRestore_resumesFromSnapshotLosingAtMostOneMilestone() = runTest {
        val sharedStore = FakeTripSessionStore()
        val envA = Env(this, parked = null, sessionStore = sharedStore)
        envA.enableAndSettleCar()
        envA.driveBtFrom(0) // 39 intervals * 30 m/s = 1170 m; last persisted milestone should be <= 1170

        val persistedDistance = sharedStore.current?.distanceMeters
        checkNotNull(persistedDistance) { "expected a snapshot to have been persisted past a 250 m milestone" }
        assertTrue(1_170L - persistedDistance <= 250L, "lost more than 250 m: persisted=$persistedDistance actual=1170")

        // envA is simply abandoned here (a process kill never calls setEnabled(false)).
        val envB = Env(this, parked = null, sessionStore = sharedStore)
        envB.enableAndSettleCar() // loads the snapshot and resumes into TRACKING

        val restoredStatus = envB.engine.status.value
        assertTrue(restoredStatus is com.hopcape.odo.core.triptracker.TrackingStatus.Tracking)
    }

    @Test
    fun taxiScenario_gpsStartFarFromParkedLocation_recordsNothing() = runTest {
        val parkedFarAway = ParkedLocation(car.id, point(19.30, 73.10), baseInstant)
        val env = Env(this, parked = parkedFarAway)
        env.enableAndSettleCar()

        env.settleMotion(MotionKind.IN_VEHICLE) // GPS candidate starts, no presence involved
        advanceTimeBy(46.seconds)
        env.fixes.emit(TraceBuilder.sample(tick = 0, lat = 19.00, lon = 72.80, accuracyM = 10f))
        env.advance()

        assertEquals(0, env.tripRepository.added.size)
        assertTrue(env.engine.status.value !is com.hopcape.odo.core.triptracker.TrackingStatus.Tracking)
    }

    @Test
    fun gapInferredTwin_createdWhenBtTripStartsAwayFromParkedLocation() = runTest {
        // Recorded well before the drive starts, so the twin's window (parked.recordedAt
        // .. trip.startedAt) is never zero-length even though this test's own virtual
        // clock hasn't moved yet at trip-start time.
        val parkedFarAway = ParkedLocation(car.id, point(19.30, 73.10), baseInstant - 10.minutes)
        val env = Env(this, parked = parkedFarAway)
        env.enableAndSettleCar()

        env.driveBtFrom(0) // first fix is at (19.00, 72.80), far from the parked point
        val trip = env.stopBtAndFinalize()

        assertEquals(TripMode.BT_VERIFIED, trip.mode)
        val gap = env.tripRepository.added.singleOrNull { it.mode == TripMode.GAP_INFERRED }
        checkNotNull(gap) { "expected a GAP_INFERRED twin, got: ${env.tripRepository.added.map { it.mode }}" }
        assertEquals(TripStatus.NEEDS_CONFIRMATION, gap.status)
    }

    @Test
    fun disableMidTrip_finalizesTheInProgressSession() = runTest {
        val env = Env(this, parked = null)
        env.enableAndSettleCar()
        env.driveBtFrom(0)

        // A trip's window must be non-empty (Trip.create requires endedAt > startedAt);
        // nothing else in this test advances the virtual clock before disabling.
        advanceTimeBy(5.seconds)
        env.engine.setEnabled(false)
        env.advance()

        assertEquals(1, env.tripRepository.added.size)
        val trip = env.tripRepository.added.single()
        assertEquals(1_170L, trip.distance.meters)
    }

    // ---- test harness ----

    private inner class Env(
        private val scope: TestScope,
        parked: ParkedLocation?,
        val sessionStore: FakeTripSessionStore = FakeTripSessionStore(),
    ) {
        val presence = MutableSharedFlow<VehiclePresence>(extraBufferCapacity = 16)
        val motion = MutableSharedFlow<MotionSignal>(extraBufferCapacity = 16)
        val fixes = MutableSharedFlow<LocationSample>(extraBufferCapacity = 16)
        val foregroundSession = FakeTripForegroundSession()
        val tripRepository = FakeTripRepository(parked)
        val telemetry = TripTrackerTelemetry(logger = NoopLogger, analytics = NoopAnalytics, tracer = NoopTracer, crash = NoopCrash)

        val engine = TripTrackerEngine(
            locationProvider = FakeLocationProvider(fixes),
            motionSource = FakeMotionActivitySource(motion),
            presenceSource = FakeVehiclePresenceSource(presence),
            foregroundSession = foregroundSession,
            sessionStore = sessionStore,
            preconditions = FakeTrackingPreconditions,
            carRepository = FakeCarRepository(car),
            tripRepository = tripRepository,
            finalizer = TripFinalizer(
                ids = FakeIdGenerator,
                tripRepository = tripRepository,
                routeEstimator = CurvatureFactorRouteEstimator(config),
                config = config,
                telemetry = telemetry,
            ),
            telemetry = telemetry,
            config = config,
            // backgroundScope: the engine's signal-source collectors and timer jobs are
            // meant to outlive a single test step, and one test (kill/restore) never
            // cancels them at all — runTest would otherwise fail on leftover jobs.
            scope = scope.backgroundScope,
            now = { baseInstant + scope.currentTime.milliseconds },
        )

        suspend fun enableAndSettleCar() {
            engine.observeCar()
            advance()
            engine.setEnabled(true)
            advance()
        }

        suspend fun settleMotion(kind: MotionKind) {
            repeat(config.motionDebounceConsecutiveReadings) {
                motion.emit(MotionSignal(kind, 90, baseInstant))
                advance()
            }
        }

        /** BT-verified drive: connect presence, settle IN_VEHICLE, then 39 one-second, 30 m/s fixes. */
        suspend fun driveBtFrom(startTick: Int) {
            if (startTick == 0) {
                presence.emit(VehiclePresence.Connected("aa:bb"))
                advance()
                settleMotion(MotionKind.IN_VEHICLE)
            }
            for (i in 0..39) {
                val tick = startTick + i
                fixes.emit(
                    TraceBuilder.sample(
                        tick = tick,
                        lat = 19.00 + tick * 0.0003,
                        lon = 72.80,
                        accuracyM = 10f,
                        speedMps = 30f,
                    ),
                )
                advance()
            }
        }

        suspend fun stopBtAndFinalize(): Trip {
            settleMotion(MotionKind.STILL)
            presence.emit(VehiclePresence.Disconnected)
            advance()
            scope.advanceTimeBy(config.stitchWindow + 1.seconds)
            advance()
            return tripRepository.added.first { it.mode != TripMode.GAP_INFERRED }
        }

        fun advance() = scope.runCurrent()
    }
}

private fun point(lat: Double, lon: Double): GeoPoint = GeoPoint.of(lat, lon).getOrNull()!!

// ---- fakes ----

private class FakeLocationProvider(private val flow: Flow<LocationSample>) : LocationProvider {
    override fun fixes(spec: com.hopcape.odo.core.triptracker.model.FixRequest): Flow<LocationSample> = flow
    override suspend fun lastKnown(): LocationSample? = null
}

private class FakeMotionActivitySource(private val flow: Flow<MotionSignal>) : MotionActivitySource {
    override fun signals(): Flow<MotionSignal> = flow
}

private class FakeVehiclePresenceSource(private val flow: Flow<VehiclePresence>) : VehiclePresenceSource {
    override fun presence(): Flow<VehiclePresence> = flow
}

private class FakeTripForegroundSession : TripForegroundSession {
    var started = 0
    var stopped = 0
    override fun start() {
        started++
    }
    override fun stop() {
        stopped++
    }
}

private class FakeTripSessionStore : TripSessionStore {
    var current: SessionSnapshot? = null
    override suspend fun save(snapshot: SessionSnapshot) {
        current = snapshot
    }
    override suspend fun load(): SessionSnapshot? = current
    override suspend fun clear() {
        current = null
    }
}

private object FakeTrackingPreconditions : TrackingPreconditions {
    override fun status(): TrackingReadiness = TrackingReadiness(true, true, true, true, true)
}

private class FakeCarRepository(private val car: Car) : CarRepository {
    override suspend fun add(car: Car): Either<DomainError, Car> = throw NotImplementedError()
    override suspend fun update(car: Car): Either<DomainError, Car> = throw NotImplementedError()
    override fun observePrimaryCar(): Flow<Car?> = flowOf(car)
    override fun observe(id: CarId): Flow<Car?> = flowOf(car)
    override suspend fun softDelete(id: CarId): Either<DomainError, Unit> = throw NotImplementedError()
}

private class FakeTripRepository(var parked: ParkedLocation?) : TripRepository {
    val added = mutableListOf<Trip>()

    override suspend fun add(trip: Trip): Either<DomainError, Trip> {
        added += trip
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
    override suspend fun parkedLocation(carId: CarId): ParkedLocation? = parked
}

private object FakeIdGenerator : IdGenerator {
    private var counter = 0
    override fun newId(): String = "trip-${counter++}"
}

private object NoopLogger : Logger {
    override fun log(level: LogLevel, tag: String, event: String, traceContext: TraceContext?, fields: Map<String, Any?>) = Unit
    override fun flush() = Unit
}

private object NoopAnalytics : AnalyticsTracker {
    override fun identify(traits: UserTraits) = Unit
    override fun track(eventName: String, properties: Map<String, Any?>) = Unit
    override fun setConsent(status: ConsentStatus) = Unit
    override fun flush() = Unit
}

private object NoopTracer : PerformanceTracer {
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

private object NoopCrash : CrashRecorder {
    override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) = Unit
    override fun leaveBreadcrumb(tag: String, message: String) = Unit
    override fun setCustomKey(key: String, value: Any?) = Unit
    override fun setUserId(userId: String?) = Unit
}
