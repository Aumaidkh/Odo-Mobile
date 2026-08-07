package com.hopcape.odo.core.data.cost

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.cost.model.FuelFillId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.payment.model.PaymentMethod
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuelFillRepositoryImplTest {

    private lateinit var driver: JdbcSqliteDriver

    private fun newDb(): OdoDatabase {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private data class StoredRow(val syncStatus: String, val remoteVersion: String?)

    /** No generated `selectById` exists for `fuel_fills` yet, so this reads the raw row. */
    private fun rowFor(id: String): StoredRow? = driver.executeQuery(
        identifier = null,
        sql = "SELECT sync_status, remote_version FROM fuel_fills WHERE id = ?",
        mapper = { cursor ->
            QueryResult.Value(
                if (cursor.next().value) StoredRow(cursor.getString(0)!!, cursor.getString(1)) else null,
            )
        },
        parameters = 1,
    ) { bindString(0, id) }.value

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

    private fun repo(db: OdoDatabase, scheduler: SyncScheduler = RecordingScheduler()) =
        FuelFillRepositoryImpl(
            local = SqlDelightFuelFillLocalDataSource(database = db),
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
        paidVia = PaymentMethod.UPI,
        transactionRef = "txn-1",
    ).getOrNull()!!

    @Test
    fun add_storesTheFillAsPending() = runTest {
        val db = newDb()

        val result = repo(db).add(fill())

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(SyncStatus.PENDING.name, rowFor("fill-1")?.syncStatus)
        assertNull(rowFor("fill-1")?.remoteVersion)
    }

    @Test
    fun add_asksForASync() = runTest {
        val db = newDb()
        val scheduler = RecordingScheduler()

        assertTrue(repo(db, scheduler).add(fill()).isRight())

        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }
}
