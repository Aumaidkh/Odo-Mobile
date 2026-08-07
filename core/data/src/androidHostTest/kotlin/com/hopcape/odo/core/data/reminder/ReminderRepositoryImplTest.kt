package com.hopcape.odo.core.data.reminder

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
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.reminder.model.CustomReminder
import com.hopcape.odo.core.domain.reminder.model.ReminderCadence
import com.hopcape.odo.core.domain.reminder.model.ReminderDismissal
import com.hopcape.odo.core.domain.reminder.model.ReminderId
import com.hopcape.odo.core.domain.reminder.model.ReminderKind
import com.hopcape.odo.core.domain.reminder.model.ReminderPreset
import com.hopcape.odo.core.domain.shared.DomainError
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
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Orchestration only — error mapping, id generation, the owner lookup, and sync
 * scheduling. The SQL behaviour these used to exercise through a real database now lives
 * in [SqlDelightReminderLocalDataSourceTest]; this suite drives [ReminderRepositoryImpl]
 * against a [FakeReminderLocalDataSource] instead.
 */
class ReminderRepositoryImplTest {

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

    private class SequentialIdGenerator : IdGenerator {
        private var next = 0
        override fun newId(): String = "gen-${++next}"
    }

    private data class DismissalCall(val id: String, val carId: CarId, val ownerId: OwnerId, val dismissal: ReminderDismissal)

    private class FakeReminderLocalDataSource(
        private val insertThrows: Throwable? = null,
        private val updateResult: Boolean = true,
        private val updateThrows: Throwable? = null,
        private val softDeleteThrows: Throwable? = null,
        private val customByCar: Flow<List<CustomReminder>> = flowOf(emptyList()),
        private val byId: Flow<CustomReminder?> = flowOf(null),
        private val dismissals: Flow<List<ReminderDismissal>> = flowOf(emptyList()),
        private val insertDismissalThrows: Throwable? = null,
    ) : ReminderLocalDataSource {
        var inserted: CustomReminder? = null
            private set
        var updated: CustomReminder? = null
            private set
        var softDeleted: ReminderId? = null
            private set
        var dismissalCall: DismissalCall? = null
            private set

        override suspend fun insert(reminder: CustomReminder) {
            insertThrows?.let { throw it }
            inserted = reminder
        }

        override suspend fun update(reminder: CustomReminder): Boolean {
            updateThrows?.let { throw it }
            updated = reminder
            return updateResult
        }

        override suspend fun softDelete(id: ReminderId) {
            softDeleteThrows?.let { throw it }
            softDeleted = id
        }

        override fun observeCustomByCar(carId: CarId): Flow<List<CustomReminder>> = customByCar
        override fun observeById(id: ReminderId): Flow<CustomReminder?> = byId
        override fun observeDismissals(carId: CarId): Flow<List<ReminderDismissal>> = dismissals

        override suspend fun insertDismissal(id: String, carId: CarId, ownerId: OwnerId, dismissal: ReminderDismissal) {
            insertDismissalThrows?.let { throw it }
            dismissalCall = DismissalCall(id, carId, ownerId, dismissal)
        }
    }

    /**
     * A fresh, unexercised sync stack — [ReminderRepositoryImpl] still takes a
     * [SyncRunner] to construct, but nothing in this suite calls `syncWith`, so a
     * throwaway in-memory DB is all it needs.
     */
    private fun repo(
        local: ReminderLocalDataSource,
        scheduler: SyncScheduler = RecordingScheduler(),
        ids: IdGenerator = SequentialIdGenerator(),
        owners: OwnerId = ownerId,
    ): ReminderRepositoryImpl {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        val db = OdoDatabase(driver)
        return ReminderRepositoryImpl(
            local = local,
            telemetry = DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = NoopCrash),
            scheduler = scheduler,
            ids = ids,
            owners = { owners },
            runner = SyncRunner(
                entity = SyncEntity.REMINDERS,
                table = ReminderSyncTable(database = db, remote = FakeReminderRemoteDataSource(), carId = { null }),
                database = db,
                telemetry = silentSyncTelemetry(),
            ),
        )
    }

    private fun reminder(
        id: String = "rem-1",
        cadence: ReminderCadence = ReminderCadence.EveryDays(15),
    ) = CustomReminder.reconstitute(
        id = ReminderId(id),
        ownerId = ownerId,
        carId = carId,
        title = "Air pressure check",
        cadence = cadence,
        startsOn = LocalDate(2026, 8, 10),
        at = LocalTime(9, 0),
        paused = false,
        addedOn = null,
        preset = ReminderPreset.AIR_PRESSURE,
        anchorKm = null,
    )

    /* ------------------------- custom reminders ------------------------- */

    @Test
    fun add_success_writesThroughLocalAndAsksForASync() = runTest {
        val local = FakeReminderLocalDataSource()
        val scheduler = RecordingScheduler()

        val result = repo(local, scheduler).add(reminder())

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals("rem-1", local.inserted?.id?.value)
        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun add_localThrows_isPersistenceFailure() = runTest {
        val local = FakeReminderLocalDataSource(insertThrows = RuntimeException("disk full"))

        val result = repo(local).add(reminder())

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
    }

    @Test
    fun update_localAnswersTrue_asksForASync() = runTest {
        val local = FakeReminderLocalDataSource(updateResult = true)
        val scheduler = RecordingScheduler()

        val result = repo(local, scheduler).update(reminder())

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun update_localAnswersFalse_isReminderNotFound() = runTest {
        val local = FakeReminderLocalDataSource(updateResult = false)

        val result = repo(local).update(reminder(id = "rem-gone"))

        assertIs<DomainError.ReminderNotFound>(result.leftOrNull())
    }

    @Test
    fun softDelete_success_asksForASync() = runTest {
        val local = FakeReminderLocalDataSource()
        val scheduler = RecordingScheduler()

        val result = repo(local, scheduler).softDelete(ReminderId("rem-1"))

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(ReminderId("rem-1"), local.softDeleted)
        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun aSchedulerThatThrows_doesNotFailTheWrite() = runTest {
        val local = FakeReminderLocalDataSource()
        val exploding = object : SyncScheduler {
            override fun scheduleStartupSync() = Unit
            override fun requestSync(reason: SyncReason): Nothing = error("no WorkManager here")
        }

        val result = repo(local, exploding).add(reminder())

        assertTrue(result.isRight(), "scheduling is not part of the write's success: $result")
    }

    /* ------------------------- dismissals: id generation and owner lookup ------------------------- */

    @Test
    fun dismiss_generatesAnIdAndTakesTheOwnerFromItsProvider() = runTest {
        val local = FakeReminderLocalDataSource()
        val dismissal = ReminderDismissal(ReminderKind.INSURANCE_EXPIRY, LocalDate(2026, 9, 1))

        val result = repo(local, ids = SequentialIdGenerator(), owners = OwnerId("owner-9")).dismiss(carId, dismissal)

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals("gen-1", local.dismissalCall?.id)
        assertEquals(carId, local.dismissalCall?.carId)
        assertEquals(OwnerId("owner-9"), local.dismissalCall?.ownerId)
        assertEquals(dismissal, local.dismissalCall?.dismissal)
    }

    @Test
    fun dismiss_asksForASync() = runTest {
        val local = FakeReminderLocalDataSource()
        val scheduler = RecordingScheduler()

        repo(local, scheduler).dismiss(carId, ReminderDismissal(ReminderKind.PUC_EXPIRY, LocalDate(2026, 9, 1)))

        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun dismiss_localThrows_isPersistenceFailure() = runTest {
        val local = FakeReminderLocalDataSource(insertDismissalThrows = RuntimeException("locked"))

        val result = repo(local).dismiss(carId, ReminderDismissal(ReminderKind.PUC_EXPIRY, LocalDate(2026, 9, 1)))

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
    }

    /* ------------------------- observe flows ------------------------- */

    @Test
    fun observeCustom_passesThroughTheLocalStream() = runTest {
        val expected = listOf(reminder())
        val local = FakeReminderLocalDataSource(customByCar = flowOf(expected))

        assertEquals(expected, repo(local).observeCustom(carId).first())
    }

    @Test
    fun observeCustom_localThrows_emitsEmptyListInstead() = runTest {
        val local = FakeReminderLocalDataSource(customByCar = flow { throw RuntimeException("read failed") })

        assertEquals(emptyList(), repo(local).observeCustom(carId).first())
    }

    @Test
    fun observe_byId_passesThroughTheLocalStream() = runTest {
        val expected = reminder()
        val local = FakeReminderLocalDataSource(byId = flowOf(expected))

        assertEquals(expected, repo(local).observe(ReminderId("rem-1")).first())
    }

    @Test
    fun observe_byId_localThrows_emitsNullInstead() = runTest {
        val local = FakeReminderLocalDataSource(byId = flow { throw RuntimeException("read failed") })

        assertNull(repo(local).observe(ReminderId("rem-1")).first())
    }

    @Test
    fun observeDismissals_passesThroughTheLocalStream() = runTest {
        val expected = listOf(ReminderDismissal(ReminderKind.PUC_EXPIRY, LocalDate(2026, 9, 1)))
        val local = FakeReminderLocalDataSource(dismissals = flowOf(expected))

        assertEquals(expected, repo(local).observeDismissals(carId).first())
    }

    @Test
    fun observeDismissals_localThrows_emitsEmptyListInstead() = runTest {
        val local = FakeReminderLocalDataSource(dismissals = flow { throw RuntimeException("read failed") })

        assertEquals(emptyList(), repo(local).observeDismissals(carId).first())
    }
}
