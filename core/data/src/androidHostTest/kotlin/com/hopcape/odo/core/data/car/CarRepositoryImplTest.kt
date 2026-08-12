package com.hopcape.odo.core.data.car

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Orchestration only — error mapping, telemetry, sync scheduling. The SQL behaviour these
 * used to exercise through a real database now lives in [SqlDelightCarLocalDataSourceTest];
 * this suite drives [CarRepositoryImpl] against a [FakeCarLocalDataSource] instead.
 */
class CarRepositoryImplTest {

    private val ownerId = OwnerId("owner-1")

    /** Records what the repository asked the scheduler for. */
    private class RecordingScheduler : SyncScheduler {
        val requested = mutableListOf<SyncReason>()
        override fun scheduleStartupSync() = Unit
        override fun requestSync(reason: SyncReason) { requested += reason }
    }

    /** A scheduler that is simply broken — scheduling failure must never fail the write. */
    private class ThrowingScheduler : SyncScheduler {
        override fun scheduleStartupSync() = Unit
        override fun requestSync(reason: SyncReason): Nothing = error("scheduler unavailable")
    }

    private class FakeCarLocalDataSource(
        private val insertThrows: Throwable? = null,
        private val updateResult: Boolean = true,
        private val updateThrows: Throwable? = null,
        private val softDeleteThrows: Throwable? = null,
        private val primary: Flow<Car?> = flowOf(null),
        private val byId: Flow<Car?> = flowOf(null),
    ) : CarLocalDataSource {
        var inserted: Car? = null
            private set
        var updated: Car? = null
            private set
        var softDeleted: CarId? = null
            private set

        override suspend fun insert(car: Car) {
            insertThrows?.let { throw it }
            inserted = car
        }

        override suspend fun update(car: Car): Boolean {
            updateThrows?.let { throw it }
            updated = car
            return updateResult
        }

        override suspend fun softDelete(id: CarId) {
            softDeleteThrows?.let { throw it }
            softDeleted = id
        }

        override fun observePrimary(): Flow<Car?> = primary
        override fun observeById(id: CarId): Flow<Car?> = byId
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

    private object NoopCrash : CrashRecorder {
        override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) = Unit
        override fun leaveBreadcrumb(tag: String, message: String) = Unit
        override fun setCustomKey(key: String, value: Any?) = Unit
        override fun setUserId(userId: String?) = Unit
    }

    private fun repo(local: CarLocalDataSource, scheduler: SyncScheduler = RecordingScheduler()) =
        CarRepositoryImpl(
            local = local,
            telemetry = DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = NoopCrash),
            scheduler = scheduler,
        )

    private fun car(id: String = "car-1", isPrimary: Boolean = true): Car = Car.create(
        id = CarId(id),
        ownerId = ownerId,
        make = "Maruti Suzuki",
        model = "Swift",
        year = 2020,
        fuelType = FuelType.PETROL,
        odometerKm = 45_000,
        registrationNumber = "mh 12 ab 1234",
        purchaseYear = 2021,
        isPrimary = isPrimary,
    ).getOrNull()!!

    @Test
    fun add_success_writesThroughLocalAndAsksForASync() = runTest {
        val local = FakeCarLocalDataSource()
        val scheduler = RecordingScheduler()

        val result = repo(local, scheduler).add(car())

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals("car-1", local.inserted?.id?.value)
        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun add_localThrows_isPersistenceFailure() = runTest {
        val local = FakeCarLocalDataSource(insertThrows = RuntimeException("disk full"))

        val result = repo(local).add(car())

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
    }

    @Test
    fun update_localAnswersFalse_isCarNotFound() = runTest {
        val local = FakeCarLocalDataSource(updateResult = false)
        val scheduler = RecordingScheduler()

        val result = repo(local, scheduler).update(car())

        assertIs<DomainError.CarNotFound>(result.leftOrNull())
        assertTrue(scheduler.requested.isEmpty(), "a not-found update must not ask for a sync")
    }

    @Test
    fun update_localAnswersTrue_asksForASync() = runTest {
        val local = FakeCarLocalDataSource(updateResult = true)
        val scheduler = RecordingScheduler()

        val result = repo(local, scheduler).update(car())

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals("car-1", local.updated?.id?.value)
        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun update_localThrows_isPersistenceFailure() = runTest {
        val local = FakeCarLocalDataSource(updateThrows = RuntimeException("locked"))

        val result = repo(local).update(car())

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
    }

    @Test
    fun softDelete_success_asksForASync() = runTest {
        val local = FakeCarLocalDataSource()
        val scheduler = RecordingScheduler()

        val result = repo(local, scheduler).softDelete(CarId("car-1"))

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(CarId("car-1"), local.softDeleted)
        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun softDelete_localThrows_isPersistenceFailure() = runTest {
        val local = FakeCarLocalDataSource(softDeleteThrows = RuntimeException("boom"))

        val result = repo(local).softDelete(CarId("car-1"))

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
    }

    @Test
    fun add_schedulingFailure_stillSucceeds() = runTest {
        val local = FakeCarLocalDataSource()

        val result = repo(local, ThrowingScheduler()).add(car())

        assertTrue(result.isRight(), "a broken scheduler must not fail an already-committed write")
    }

    @Test
    fun observePrimaryCar_passesThroughTheLocalStream() = runTest {
        val expected = car()
        val local = FakeCarLocalDataSource(primary = flowOf(expected))

        assertEquals(expected, repo(local).observePrimaryCar().first())
    }

    @Test
    fun observePrimaryCar_localThrows_emitsNullInstead() = runTest {
        val local = FakeCarLocalDataSource(primary = flow { throw RuntimeException("read failed") })

        assertNull(repo(local).observePrimaryCar().first())
    }

    @Test
    fun observe_passesThroughTheLocalStream() = runTest {
        val expected = car()
        val local = FakeCarLocalDataSource(byId = flowOf(expected))

        assertEquals(expected, repo(local).observe(CarId("car-1")).first())
    }

    @Test
    fun observe_localThrows_emitsNullInstead() = runTest {
        val local = FakeCarLocalDataSource(byId = flow { throw RuntimeException("read failed") })

        assertNull(repo(local).observe(CarId("car-1")).first())
    }
}
