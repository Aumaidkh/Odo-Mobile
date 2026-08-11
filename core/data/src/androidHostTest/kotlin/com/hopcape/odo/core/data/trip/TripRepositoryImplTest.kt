package com.hopcape.odo.core.data.trip

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.trip.model.GeoPoint
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.model.Trip
import com.hopcape.odo.core.domain.trip.model.TripDistance
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.odo.core.domain.trip.model.TripStatus
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Orchestration only — error mapping and telemetry. The SQL behaviour lives in
 * [com.hopcape.odo.infrastructure.database.trip.SqlDelightTripLocalDataSourceTest] instead,
 * against a real database. No [com.hopcape.odo.core.sync.SyncScheduler]: [TripRepositoryImpl]
 * takes none — there is no `SyncEntity.TRIPS`/`Syncable` yet.
 */
class TripRepositoryImplTest {

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")

    private fun trip(id: String = "trip-1"): Trip = Trip.create(
        id = TripId(id),
        carId = carId,
        ownerId = ownerId,
        startedAt = Instant.parse("2026-08-07T08:00:00Z"),
        endedAt = Instant.parse("2026-08-07T08:20:00Z"),
        distance = TripDistance.of(5_000),
        estimatedDistance = TripDistance.ZERO,
        mode = TripMode.BT_VERIFIED,
        status = TripStatus.RECORDED,
    ).getOrNull()!!

    private fun repo(local: TripLocalDataSource, crash: CrashRecorder = RecordingCrash()) = TripRepositoryImpl(
        local = local,
        telemetry = DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = crash),
    )

    @Test
    fun add_success_writesThroughLocal() = runTest {
        val local = FakeTripLocalDataSource()
        val result = repo(local).add(trip())
        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals("trip-1", local.inserted?.id?.value)
    }

    @Test
    fun add_failure_isRecordedAsANonFatal() = runTest {
        val local = FakeTripLocalDataSource(insertThrows = RuntimeException("disk full"))
        val crash = RecordingCrash()
        val result = repo(local, crash = crash).add(trip())
        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
        assertEquals(1, crash.nonFatals.size, "a swallowed exception must still reach the dashboard")
    }

    @Test
    fun addWithParked_success_writesThroughLocal() = runTest {
        val local = FakeTripLocalDataSource()
        val gap = trip(id = "trip-gap")
        val parked = ParkedLocation(carId, GeoPoint.of(19.0, 72.8).getOrNull()!!, Instant.parse("2026-08-07T08:20:00Z"))
        val result = repo(local).addWithParked(trip(), gap, parked)
        assertTrue(result.isRight())
        assertEquals("trip-1", local.insertedWithParked?.first?.id?.value)
        assertEquals("trip-gap", local.insertedWithParked?.second?.id?.value)
    }

    @Test
    fun setStatus_missingTrip_returnsTripNotFound() = runTest {
        val local = FakeTripLocalDataSource(updateStatusResult = false)
        val result = repo(local).setStatus(TripId("nope"), TripStatus.CONFIRMED)
        assertEquals(DomainError.TripNotFound, result.leftOrNull())
    }

    @Test
    fun setStatus_success_isRight() = runTest {
        val local = FakeTripLocalDataSource(updateStatusResult = true)
        val result = repo(local).setStatus(TripId("trip-1"), TripStatus.CONFIRMED)
        assertTrue(result.isRight())
    }

    @Test
    fun observe_localFailure_emitsEmptyListInstead() = runTest {
        val local = FakeTripLocalDataSource(byCarThrows = RuntimeException("cursor closed"))
        val crash = RecordingCrash()
        val emitted = repo(local, crash = crash).observe(carId)
        assertEquals(emptyList(), emitted.first())
        assertEquals(1, crash.nonFatals.size)
    }

    @Test
    fun countedSince_localFailure_returnsEmptyList() = runTest {
        val local = FakeTripLocalDataSource(countedSinceThrows = RuntimeException("boom"))
        val crash = RecordingCrash()
        val result = repo(local, crash = crash).countedSince(carId, Instant.parse("2026-08-07T00:00:00Z"))
        assertEquals(emptyList(), result)
        assertEquals(1, crash.nonFatals.size)
    }

    @Test
    fun parkedLocation_delegatesToLocal() = runTest {
        val parked = ParkedLocation(carId, GeoPoint.of(19.0, 72.8).getOrNull()!!, Instant.parse("2026-08-07T08:20:00Z"))
        val local = FakeTripLocalDataSource(parkedLocationResult = parked)
        assertEquals(parked, repo(local).parkedLocation(carId))
    }

    @Test
    fun countedBetween_localFailure_returnsEmptyList() = runTest {
        val local = FakeTripLocalDataSource(countedBetweenThrows = RuntimeException("boom"))
        val crash = RecordingCrash()
        val result = repo(local, crash = crash).countedBetween(
            carId,
            Instant.parse("2026-08-07T00:00:00Z"),
            Instant.parse("2026-08-08T00:00:00Z"),
        )
        assertEquals(emptyList(), result)
        assertEquals(1, crash.nonFatals.size)
    }

    @Test
    fun deleteAllForCar_success_writesThroughLocal() = runTest {
        val local = FakeTripLocalDataSource()
        val result = repo(local).deleteAllForCar(carId)
        assertTrue(result.isRight())
        assertEquals(carId, local.deletedForCar)
    }

    @Test
    fun deleteAllForCar_failure_isRecordedAsANonFatal() = runTest {
        val local = FakeTripLocalDataSource(deleteAllForCarThrows = RuntimeException("disk full"))
        val crash = RecordingCrash()
        val result = repo(local, crash = crash).deleteAllForCar(carId)
        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
        assertEquals(1, crash.nonFatals.size)
    }

    @Test
    fun forgetRoutes_success_writesThroughLocal() = runTest {
        val local = FakeTripLocalDataSource()
        val result = repo(local).forgetRoutes()
        assertTrue(result.isRight())
        assertTrue(local.routesForgotten)
    }

    @Test
    fun forgetRoutes_failure_isRecordedAsANonFatal() = runTest {
        // An owner who turned the switch off and was told nothing would be left holding a
        // phone that still has their routes on it, so this one has to be loud.
        val local = FakeTripLocalDataSource(forgetRoutesThrows = RuntimeException("disk full"))
        val crash = RecordingCrash()
        val result = repo(local, crash = crash).forgetRoutes()
        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
        assertEquals(1, crash.nonFatals.size)
    }

    // ---- fakes ----

    private class FakeTripLocalDataSource(
        private val insertThrows: Throwable? = null,
        private val updateStatusResult: Boolean = true,
        private val byCarThrows: Throwable? = null,
        private val countedSinceThrows: Throwable? = null,
        private val countedBetweenThrows: Throwable? = null,
        private val parkedLocationResult: ParkedLocation? = null,
        private val deleteAllForCarThrows: Throwable? = null,
        private val forgetRoutesThrows: Throwable? = null,
    ) : TripLocalDataSource {
        var inserted: Trip? = null
            private set
        var insertedWithParked: Pair<Trip, Trip?>? = null
            private set
        var deletedForCar: CarId? = null
            private set

        override suspend fun insert(trip: Trip) {
            insertThrows?.let { throw it }
            inserted = trip
        }

        override suspend fun insertWithParked(trip: Trip, gap: Trip?, parked: ParkedLocation) {
            insertThrows?.let { throw it }
            insertedWithParked = trip to gap
        }

        override suspend fun updateStatus(id: TripId, status: TripStatus): Boolean = updateStatusResult

        override fun observeByCar(carId: CarId): Flow<List<Trip>> =
            if (byCarThrows != null) flow { throw byCarThrows } else flowOf(emptyList())

        override fun observeNeedingConfirmation(carId: CarId): Flow<List<Trip>> = flowOf(emptyList())

        override suspend fun countedSince(carId: CarId, after: Instant): List<Trip> {
            countedSinceThrows?.let { throw it }
            return emptyList()
        }

        override suspend fun countedBetween(carId: CarId, from: Instant, to: Instant): List<Trip> {
            countedBetweenThrows?.let { throw it }
            return emptyList()
        }

        override suspend fun parkedLocation(carId: CarId): ParkedLocation? = parkedLocationResult

        override suspend fun deleteAllForCar(carId: CarId) {
            deleteAllForCarThrows?.let { throw it }
            deletedForCar = carId
        }

        var routesForgotten = false
            private set

        override suspend fun forgetRoutes() {
            forgetRoutesThrows?.let { throw it }
            routesForgotten = true
        }
    }

    private object NoopLogger : Logger {
        override fun log(level: LogLevel, tag: String, event: String, traceContext: TraceContext?, fields: Map<String, Any?>) = Unit
        override fun flush() = Unit
    }

    private class FakeSpan(
        override val spanId: String,
        override val traceId: String,
        override val parentSpanId: String?,
        override val name: String,
    ) : Span {
        override fun setAttribute(key: String, value: Any?): Span = this
    }

    private object NoopTracer : PerformanceTracer {
        override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
            FakeSpan("span", traceId, parentSpanId, name)
        override fun endSpan(span: Span) = Unit
        override fun flush() = Unit
    }

    private class RecordingCrash : CrashRecorder {
        val nonFatals = mutableListOf<Throwable>()
        override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) {
            nonFatals += throwable
        }
        override fun leaveBreadcrumb(tag: String, message: String) = Unit
        override fun setCustomKey(key: String, value: Any?) = Unit
        override fun setUserId(userId: String?) = Unit
    }
}
