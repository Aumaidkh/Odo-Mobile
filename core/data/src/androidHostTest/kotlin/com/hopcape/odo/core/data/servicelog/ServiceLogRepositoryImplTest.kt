package com.hopcape.odo.core.data.servicelog

import com.hopcape.odo.core.data.sync.noopBlobUploader
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncRunner
import com.hopcape.odo.core.data.sync.silentSyncTelemetry
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.sync.SyncEntity
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
 * Orchestration only — error mapping, telemetry, sync scheduling, and the two domain
 * decisions [ServiceLogRepositoryImpl.odometerReadings] makes on top of what the local data
 * source answers. The SQL behaviour these used to exercise through a real database now
 * lives in [SqlDelightServiceLogLocalDataSourceTest]; this suite drives
 * [ServiceLogRepositoryImpl] against a [FakeServiceLogLocalDataSource] instead.
 */
class ServiceLogRepositoryImplTest {

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")

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

    private class RecordingCrash : CrashRecorder {
        val nonFatals = mutableListOf<Throwable>()
        override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) {
            nonFatals += throwable
        }
        override fun leaveBreadcrumb(tag: String, message: String) = Unit
        override fun setCustomKey(key: String, value: Any?) = Unit
        override fun setUserId(userId: String?) = Unit
    }

    /** Records what the repository asked the scheduler for. */
    private class RecordingScheduler : SyncScheduler {
        val requested = mutableListOf<SyncReason>()
        override fun scheduleStartupSync() = Unit
        override fun requestSync(reason: SyncReason) { requested += reason }
    }

    /** A scheduler that is simply broken — scheduling failure must never fail the write. */
    private class ThrowingScheduler : SyncScheduler {
        override fun scheduleStartupSync() = Unit
        override fun requestSync(reason: SyncReason): Nothing = error("no WorkManager here")
    }

    private class FakeServiceLogLocalDataSource(
        private val insertThrows: Throwable? = null,
        private val updateResult: Boolean = true,
        private val updateThrows: Throwable? = null,
        private val softDeleteThrows: Throwable? = null,
        private val byCar: Flow<List<ServiceLogEntry>> = flowOf(emptyList()),
        private val byId: Flow<ServiceLogEntry?> = flowOf(null),
        private val odometerReadingsResult: List<OdometerReading> = emptyList(),
        private val odometerReadingsThrows: Throwable? = null,
        private val observedOdometerReadings: Flow<List<OdometerReading>> = flowOf(emptyList()),
    ) : ServiceLogLocalDataSource {
        var inserted: ServiceLogEntry? = null
            private set
        var updated: ServiceLogEntry? = null
            private set
        var softDeleted: ServiceLogId? = null
            private set

        override suspend fun insert(entry: ServiceLogEntry) {
            insertThrows?.let { throw it }
            inserted = entry
        }

        override suspend fun update(entry: ServiceLogEntry): Boolean {
            updateThrows?.let { throw it }
            updated = entry
            return updateResult
        }

        override suspend fun softDelete(id: ServiceLogId) {
            softDeleteThrows?.let { throw it }
            softDeleted = id
        }

        override fun observeByCar(carId: CarId): Flow<List<ServiceLogEntry>> = byCar
        override fun observeById(id: ServiceLogId): Flow<ServiceLogEntry?> = byId

        override suspend fun odometerReadings(carId: CarId): List<OdometerReading> {
            odometerReadingsThrows?.let { throw it }
            return odometerReadingsResult
        }

        override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> = observedOdometerReadings
    }

    /**
     * A fresh, unexercised sync stack — [ServiceLogRepositoryImpl] still takes a [SyncRunner]
     * to construct, but nothing in this suite calls `syncWith`, so a throwaway in-memory DB
     * is all it needs.
     */
    private fun repo(
        local: ServiceLogLocalDataSource,
        crash: CrashRecorder = RecordingCrash(),
        scheduler: SyncScheduler = RecordingScheduler(),
    ): ServiceLogRepositoryImpl {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        val db = OdoDatabase(driver)
        return ServiceLogRepositoryImpl(
            local = local,
            telemetry = DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = crash),
            scheduler = scheduler,
            runner = SyncRunner(
                entity = SyncEntity.SERVICE_LOGS,
                table = ServiceLogSyncTable(
                    database = db,
                    remote = FakeServiceLogRemoteDataSource(),
                    blobs = noopBlobUploader(),
                    carId = { null },
                ),
                database = db,
                telemetry = silentSyncTelemetry(),
            ),
        )
    }

    private fun entry(id: String = "log-1") = ServiceLogEntry.reconstitute(
        id = ServiceLogId(id),
        carId = carId,
        ownerId = ownerId,
        serviceDate = LocalDate(2026, 6, 15),
        odometerKm = 50_000,
        totalAmountPaise = 330_000,
        workshopName = "Sharma Motors",
        notes = "front pads",
        source = LogSource.MANUAL,
        billId = null,
        categories = setOf(ServiceCategory.BRAKES),
        billPhotoRef = null,
        fairness = null,
    )

    private fun reading(logId: String?, km: Int) = OdometerReading(
        logId = logId?.let(::ServiceLogId),
        date = LocalDate(2026, 6, 1),
        odometer = Distance.of(km).getOrNull()!!,
    )

    /* ------------------------- writes ------------------------- */

    @Test
    fun add_success_writesThroughLocalAndAsksForASync() = runTest {
        val local = FakeServiceLogLocalDataSource()
        val scheduler = RecordingScheduler()

        val result = repo(local, scheduler = scheduler).add(entry())

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals("log-1", local.inserted?.id?.value)
        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun update_localAnswersFalse_isServiceLogNotFound() = runTest {
        val local = FakeServiceLogLocalDataSource(updateResult = false)

        val result = repo(local).update(entry())

        assertEquals(DomainError.ServiceLogNotFound, result.leftOrNull())
    }

    @Test
    fun softDelete_success_asksForASync() = runTest {
        val local = FakeServiceLogLocalDataSource()
        val scheduler = RecordingScheduler()

        val result = repo(local, scheduler = scheduler).softDelete(ServiceLogId("log-1"))

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(ServiceLogId("log-1"), local.softDeleted)
        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun aFailedWrite_isRecordedAsANonFatal() = runTest {
        val local = FakeServiceLogLocalDataSource(insertThrows = RuntimeException("duplicate id"))
        val crash = RecordingCrash()

        val result = repo(local, crash = crash).add(entry())

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
        assertEquals(1, crash.nonFatals.size, "a swallowed exception must still reach the dashboard")
    }

    @Test
    fun everyWrite_asksForASync() = runTest {
        val local = FakeServiceLogLocalDataSource()
        val scheduler = RecordingScheduler()
        val repo = repo(local, scheduler = scheduler)

        repo.add(entry())
        repo.update(entry())
        repo.softDelete(ServiceLogId("log-1"))

        // Three writes, three asks — coalescing them is the scheduler's job, not ours.
        assertEquals(List(3) { SyncReason.LocalWrite }, scheduler.requested)
    }

    @Test
    fun aFailedWrite_doesNotAskForASync() = runTest {
        val local = FakeServiceLogLocalDataSource(updateResult = false)
        val scheduler = RecordingScheduler()

        repo(local, scheduler = scheduler).update(entry(id = "ghost"))

        assertTrue(scheduler.requested.isEmpty(), "a rejected write left nothing PENDING")
    }

    @Test
    fun aSchedulerThatThrows_doesNotFailTheWrite() = runTest {
        val local = FakeServiceLogLocalDataSource()

        val result = repo(local, scheduler = ThrowingScheduler()).add(entry())

        // The data is safely local and PENDING; the next trigger will carry it.
        assertTrue(result.isRight(), "scheduling is not part of the write's success: $result")
    }

    /* ------------------------- odometer readings: the domain decisions ------------------------- */

    /**
     * An empty result from the local data source means no live car contributed its
     * baseline — the car does not exist for this owner, which the domain reads as
     * CarNotFound. This mapping is the repository's, not the local data source's.
     */
    @Test
    fun odometerReadings_emptyLocalResult_readsAsNull() = runTest {
        val local = FakeServiceLogLocalDataSource(odometerReadingsResult = emptyList())

        assertNull(repo(local).odometerReadings(carId))
    }

    @Test
    fun odometerReadings_nonEmptyLocalResult_passesThrough() = runTest {
        val readings = listOf(reading(logId = null, km = 45_000))
        val local = FakeServiceLogLocalDataSource(odometerReadingsResult = readings)

        assertEquals(readings, repo(local).odometerReadings(carId))
    }

    /**
     * A read failure is not "no such car" — it is reported and read as an empty timeline,
     * so a legitimate write is not rejected with the wrong error.
     */
    @Test
    fun odometerReadings_localThrows_readsAsEmptyListNotNull() = runTest {
        val local = FakeServiceLogLocalDataSource(odometerReadingsThrows = RuntimeException("disk error"))

        assertEquals(emptyList(), repo(local).odometerReadings(carId))
    }

    /* ------------------------- observe flows ------------------------- */

    @Test
    fun observe_byCar_passesThroughTheLocalStream() = runTest {
        val expected = listOf(entry())
        val local = FakeServiceLogLocalDataSource(byCar = flowOf(expected))

        assertEquals(expected, repo(local).observe(carId).first())
    }

    @Test
    fun observe_byCar_localThrows_emitsEmptyListInstead() = runTest {
        val local = FakeServiceLogLocalDataSource(byCar = flow { throw RuntimeException("read failed") })

        assertEquals(emptyList(), repo(local).observe(carId).first())
    }

    @Test
    fun observe_byId_passesThroughTheLocalStream() = runTest {
        val expected = entry()
        val local = FakeServiceLogLocalDataSource(byId = flowOf(expected))

        assertEquals(expected, repo(local).observe(ServiceLogId("log-1")).first())
    }

    @Test
    fun observe_byId_localThrows_emitsNullInstead() = runTest {
        val local = FakeServiceLogLocalDataSource(byId = flow { throw RuntimeException("read failed") })

        assertNull(repo(local).observe(ServiceLogId("log-1")).first())
    }

    @Test
    fun observeOdometerReadings_passesThroughTheLocalStream() = runTest {
        val expected = listOf(reading(logId = null, km = 45_000))
        val local = FakeServiceLogLocalDataSource(observedOdometerReadings = flowOf(expected))

        assertEquals(expected, repo(local).observeOdometerReadings(carId).first())
    }

    @Test
    fun observeOdometerReadings_localThrows_emitsEmptyListInstead() = runTest {
        val local = FakeServiceLogLocalDataSource(
            observedOdometerReadings = flow { throw RuntimeException("read failed") },
        )

        assertEquals(emptyList(), repo(local).observeOdometerReadings(carId).first())
    }
}
