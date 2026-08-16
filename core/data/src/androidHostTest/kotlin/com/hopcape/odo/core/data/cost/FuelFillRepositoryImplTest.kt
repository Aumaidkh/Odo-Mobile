package com.hopcape.odo.core.data.cost

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.FillEntrySource
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.cost.model.FuelFillId
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
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Orchestration only — error mapping and sync scheduling. The SQL behaviour these used to
 * exercise through a real database now lives in [SqlDelightFuelFillLocalDataSourceTest];
 * this suite drives [FuelFillRepositoryImpl] against a [FakeFuelFillLocalDataSource]
 * instead.
 */
class FuelFillRepositoryImplTest {

    /** Records what the repository asked the scheduler for. */
    private class RecordingScheduler : SyncScheduler {
        val requested = mutableListOf<SyncReason>()
        override fun scheduleStartupSync() = Unit
        override fun requestSync(reason: SyncReason) { requested += reason }
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

    private class FakeFuelFillLocalDataSource(
        private val insertThrows: Throwable? = null,
        private val readThrows: Throwable? = null,
        private val stored: List<FuelFill> = emptyList(),
    ) : FuelFillLocalDataSource {
        var inserted: FuelFill? = null
            private set

        override suspend fun insert(fill: FuelFill) {
            insertThrows?.let { throw it }
            inserted = fill
        }

        override fun observeByCar(carId: CarId): Flow<List<FuelFill>> =
            readThrows?.let { flow { throw it } } ?: flowOf(stored)

        override suspend fun latestForCar(carId: CarId): FuelFill? {
            readThrows?.let { throw it }
            return stored.firstOrNull()
        }

        override suspend fun countBySource(carId: CarId, source: FillEntrySource): Int {
            readThrows?.let { throw it }
            return stored.count { it.entrySource == source }
        }
    }

    private fun repo(local: FuelFillLocalDataSource, scheduler: SyncScheduler = RecordingScheduler()) =
        FuelFillRepositoryImpl(
            local = local,
            telemetry = DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = NoopCrash),
            scheduler = scheduler,
        )

    private fun fill(id: String = "fill-1") = FuelFill.create(
        id = FuelFillId(id),
        carId = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        filledOn = LocalDate(2026, 8, 1),
        odometerKm = 45_000,
        quantityMilli = 32_450,
        unit = FuelUnit.LITRE,
        amountPaise = 320_000,
        today = LocalDate(2026, 8, 1),
        stationName = "HP Andheri",
        transactionRef = "txn-1",
    ).getOrNull()!!

    @Test
    fun add_success_writesThroughLocalAndAsksForASync() = runTest {
        val local = FakeFuelFillLocalDataSource()
        val scheduler = RecordingScheduler()

        val result = repo(local, scheduler).add(fill())

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals("fill-1", local.inserted?.id?.value)
        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun add_localThrows_isPersistenceFailure() = runTest {
        val local = FakeFuelFillLocalDataSource(insertThrows = RuntimeException("disk full"))

        val result = repo(local).add(fill())

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
    }

    @Test
    fun add_schedulingFailure_stillSucceeds() = runTest {
        val local = FakeFuelFillLocalDataSource()
        val exploding = object : SyncScheduler {
            override fun scheduleStartupSync() = Unit
            override fun requestSync(reason: SyncReason): Nothing = error("scheduler unavailable")
        }

        val result = repo(local, exploding).add(fill())

        assertTrue(result.isRight(), "a broken scheduler must not fail an already-committed write")
    }

    @Test
    fun observe_readFails_emitsAnEmptyListRatherThanBreakingTheStream() = runTest {
        val local = FakeFuelFillLocalDataSource(readThrows = RuntimeException("db gone"))

        val fills = repo(local).observeForCar(CarId("car-1")).first()

        assertEquals(emptyList(), fills)
    }

    @Test
    fun latest_readFails_isPersistenceFailure() = runTest {
        val local = FakeFuelFillLocalDataSource(readThrows = RuntimeException("db gone"))

        val result = repo(local).latestForCar(CarId("car-1"))

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
    }

    @Test
    fun latest_withNoFills_isNullRatherThanAFailure() = runTest {
        val result = repo(FakeFuelFillLocalDataSource()).latestForCar(CarId("car-1"))

        assertTrue(result.isRight())
        assertNull(result.getOrNull())
    }
}
