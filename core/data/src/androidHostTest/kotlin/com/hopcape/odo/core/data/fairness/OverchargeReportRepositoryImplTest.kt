package com.hopcape.odo.core.data.fairness

import com.hopcape.odo.core.data.sync.silentSyncTelemetry
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncRunner
import com.hopcape.odo.core.domain.fairness.model.OverchargeReason
import com.hopcape.odo.core.domain.fairness.model.OverchargeReport
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Orchestration only — error mapping, id generation, and sync scheduling. The SQL
 * behaviour these used to exercise through a real database now lives in
 * [SqlDelightOverchargeReportLocalDataSourceTest]; this suite drives
 * [OverchargeReportRepositoryImpl] against a [FakeOverchargeReportLocalDataSource] instead.
 */
class OverchargeReportRepositoryImplTest {

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

    /** Records what the repository asked the scheduler for. */
    private class RecordingScheduler : SyncScheduler {
        val requested = mutableListOf<SyncReason>()
        override fun scheduleStartupSync() = Unit
        override fun requestSync(reason: SyncReason) { requested += reason }
    }

    private class FakeOverchargeReportLocalDataSource(
        private val insertResult: Boolean = true,
        private val insertThrows: Throwable? = null,
    ) : OverchargeReportLocalDataSource {
        var insertedId: String? = null
            private set
        var insertedReport: OverchargeReport? = null
            private set

        override suspend fun insert(id: String, report: OverchargeReport): Boolean {
            insertThrows?.let { throw it }
            insertedId = id
            insertedReport = report
            return insertResult
        }
    }

    /**
     * A fresh, unexercised sync stack — [OverchargeReportRepositoryImpl] still takes a
     * [SyncRunner] to construct, but nothing in this suite calls `syncWith`, so a
     * throwaway in-memory DB is all it needs.
     */
    private fun repo(
        local: OverchargeReportLocalDataSource,
        scheduler: SyncScheduler = RecordingScheduler(),
        idGenerator: IdGenerator = IdGenerator { "report-1" },
    ): OverchargeReportRepositoryImpl {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        val db = OdoDatabase(driver)
        return OverchargeReportRepositoryImpl(
            local = local,
            telemetry = DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = NoopCrash),
            idGenerator = idGenerator,
            scheduler = scheduler,
            runner = SyncRunner(
                entity = SyncEntity.OVERCHARGE_REPORTS,
                table = OverchargeReportSyncTable(database = db, remote = FakeOverchargeRemoteDataSource()),
                database = db,
                telemetry = silentSyncTelemetry(),
            ),
        )
    }

    private fun report(logId: String = "log-1") = OverchargeReport(
        logId = ServiceLogId(logId),
        reason = OverchargeReason.ABOVE_MARKET_RATE,
    )

    @Test
    fun submit_success_writesThroughLocalAndAsksForASync() = runTest {
        val local = FakeOverchargeReportLocalDataSource()
        val scheduler = RecordingScheduler()

        val result = repo(local, scheduler = scheduler, idGenerator = IdGenerator { "report-1" }).submit(report())

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals("report-1", local.insertedId)
        assertEquals("log-1", local.insertedReport?.logId?.value)
        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun submit_localAnswersFalse_isServiceLogNotFound() = runTest {
        val local = FakeOverchargeReportLocalDataSource(insertResult = false)
        val scheduler = RecordingScheduler()

        val result = repo(local, scheduler = scheduler).submit(report(logId = "ghost"))

        assertEquals(DomainError.ServiceLogNotFound, result.leftOrNull())
        assertTrue(scheduler.requested.isEmpty(), "a rejected report left nothing PENDING")
    }

    @Test
    fun submit_localThrows_isPersistenceFailure() = runTest {
        val local = FakeOverchargeReportLocalDataSource(insertThrows = RuntimeException("disk full"))

        val result = repo(local).submit(report())

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
    }

    @Test
    fun submit_schedulingFailure_stillSucceeds() = runTest {
        val local = FakeOverchargeReportLocalDataSource()
        val exploding = object : SyncScheduler {
            override fun scheduleStartupSync() = Unit
            override fun requestSync(reason: SyncReason): Nothing = error("scheduler unavailable")
        }

        val result = repo(local, exploding).submit(report())

        assertTrue(result.isRight(), "a broken scheduler must not fail an already-committed write")
    }
}
