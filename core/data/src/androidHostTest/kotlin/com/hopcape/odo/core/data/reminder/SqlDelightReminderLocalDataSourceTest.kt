package com.hopcape.odo.core.data.reminder

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.reminder.model.CustomReminder
import com.hopcape.odo.core.domain.reminder.model.ReminderCadence
import com.hopcape.odo.core.domain.reminder.model.ReminderDismissal
import com.hopcape.odo.core.domain.reminder.model.ReminderId
import com.hopcape.odo.core.domain.reminder.model.ReminderKind
import com.hopcape.odo.core.domain.reminder.model.ReminderPreset
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * SQL behaviour for [SqlDelightReminderLocalDataSource] — cadence round-trips, dismissal
 * de-duplication, and the unreadable-row skip. Error mapping, id generation, the owner
 * lookup, and sync scheduling live in [ReminderRepositoryImplTest] instead, against a fake
 * port.
 */
class SqlDelightReminderLocalDataSourceTest {

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

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private fun newDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private fun local(db: OdoDatabase, now: String = "2026-08-06T10:00:00Z") = SqlDelightReminderLocalDataSource(
        database = db,
        telemetry = DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = NoopCrash),
        clock = FixedClock(Instant.parse(now)),
        dispatcher = Dispatchers.Unconfined,
    )

    private fun reminder(
        id: String = "rem-1",
        cadence: ReminderCadence = ReminderCadence.EveryDays(15),
        preset: ReminderPreset? = ReminderPreset.AIR_PRESSURE,
        anchorKm: Int? = null,
        paused: Boolean = false,
    ) = CustomReminder.reconstitute(
        id = ReminderId(id),
        ownerId = ownerId,
        carId = carId,
        title = "Air pressure check",
        cadence = cadence,
        startsOn = LocalDate(2026, 8, 10),
        at = LocalTime(9, 0),
        paused = paused,
        addedOn = null,
        preset = preset,
        anchorKm = anchorKm,
    )

    /* ------------------------- custom reminders ------------------------- */

    @Test
    fun insert_thenObserveCustom_roundTripsEveryField() = runTest {
        val db = newDb()
        local(db).insert(reminder())

        val stored = local(db).observeCustomByCar(carId).first().single()
        assertEquals("rem-1", stored.id.value)
        assertEquals("Air pressure check", stored.title.value)
        assertEquals(ReminderCadence.EveryDays(15), stored.cadence)
        assertEquals(LocalDate(2026, 8, 10), stored.startsOn)
        assertEquals(LocalTime(9, 0), stored.at)
        assertEquals(ReminderPreset.AIR_PRESSURE, stored.preset)
        assertEquals(false, stored.paused)
        // The row's created_at supplies the filing date the domain object was built without.
        assertEquals(LocalDate(2026, 8, 6), stored.addedOn)
    }

    @Test
    fun distanceCadenceKeepsItsAnchor() = runTest {
        val db = newDb()
        local(db).insert(reminder(cadence = ReminderCadence.EveryDistance(10_000), anchorKm = 42_000))

        val stored = local(db).observeCustomByCar(carId).first().single()
        assertEquals(ReminderCadence.EveryDistance(10_000), stored.cadence)
        assertEquals(42_000, stored.anchorKm)
    }

    @Test
    fun insert_stampsPending() = runTest {
        val db = newDb()
        local(db).insert(reminder())

        val row = db.reminderQueries.selectPending().executeAsList().single()
        assertEquals(SyncStatus.PENDING.name, row.sync_status)
    }

    @Test
    fun update_editsTheRowAndReturnsItToPending() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(reminder())
        db.reminderQueries.markSynced(remoteVersion = "v1", id = "rem-1")

        assertTrue(local.update(reminder(paused = true)))

        val stored = local.observeCustomByCar(carId).first().single()
        assertTrue(stored.paused)
        assertEquals(SyncStatus.PENDING.name, db.reminderQueries.selectPending().executeAsList().single().sync_status)
    }

    @Test
    fun update_unknownReminder_answersFalse() = runTest {
        assertFalse(local(newDb()).update(reminder(id = "rem-gone")))
    }

    @Test
    fun softDelete_hidesTheReminderButKeepsTheTombstone() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(reminder())

        local.softDelete(ReminderId("rem-1"))

        assertTrue(local.observeCustomByCar(carId).first().isEmpty())
        assertNull(local.observeById(ReminderId("rem-1")).first())
        // The tombstone stays, PENDING, so the deletion itself can reach the server.
        val row = db.reminderQueries.selectPending().executeAsList().single()
        assertEquals("rem-1", row.id)
        assertTrue(row.deleted_at != null)
    }

    /* ------------------------- dismissals ------------------------- */

    @Test
    fun insertDismissal_thenObserveDismissals_roundTrips() = runTest {
        val db = newDb()
        val local = local(db)
        val dismissal = ReminderDismissal(ReminderKind.INSURANCE_EXPIRY, LocalDate(2026, 9, 1))

        local.insertDismissal(id = "gen-1", carId = carId, ownerId = ownerId, dismissal = dismissal)

        assertEquals(listOf(dismissal), local.observeDismissals(carId).first())
    }

    @Test
    fun dismissingTheSameOccurrenceTwiceKeepsOneRow() = runTest {
        val db = newDb()
        val local = local(db)
        val dismissal = ReminderDismissal(ReminderKind.PUC_EXPIRY, LocalDate(2026, 9, 1))

        local.insertDismissal(id = "gen-1", carId = carId, ownerId = ownerId, dismissal = dismissal)
        // A second dismissal of the same occurrence relies on OR IGNORE + the unique index.
        local.insertDismissal(id = "gen-2", carId = carId, ownerId = ownerId, dismissal = dismissal)

        assertEquals(1, local.observeDismissals(carId).first().size)
    }

    @Test
    fun customDismissalCarriesItsReminderId() = runTest {
        val db = newDb()
        val local = local(db)
        val dismissal = ReminderDismissal(
            kind = ReminderKind.CUSTOM,
            dueOn = LocalDate(2026, 9, 1),
            customId = ReminderId("rem-1"),
        )

        local.insertDismissal(id = "gen-1", carId = carId, ownerId = ownerId, dismissal = dismissal)

        assertEquals(listOf(dismissal), local.observeDismissals(carId).first())
    }

    @Test
    fun dismissalRowsStayOutOfTheCustomList() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(reminder())
        local.insertDismissal(
            id = "gen-1",
            carId = carId,
            ownerId = ownerId,
            dismissal = ReminderDismissal(ReminderKind.CUSTOM, LocalDate(2026, 9, 1), ReminderId("rem-1")),
        )

        assertEquals(1, local.observeCustomByCar(carId).first().size)
        assertEquals(1, local.observeDismissals(carId).first().size)
    }

    /* ------------------------- unreadable rows ------------------------- */

    @Test
    fun aRowWithAnUnknownRepeatKindIsSkippedNotCrashed() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(reminder())
        db.reminderQueries.insertReminder(
            id = "rem-newer",
            carId = carId.value,
            ownerId = ownerId.value,
            dueDate = "2026-09-01",
            title = "Written by a newer build",
            isPaused = 0,
            startsOn = "2026-09-01",
            remindAt = "09:00",
            repeatKind = "LUNAR_CYCLE",
            repeatEveryDays = null,
            repeatEveryKm = null,
            anchorKm = null,
            preset = null,
            now = "2026-08-06T10:00:00Z",
            syncStatus = SyncStatus.SYNCED.name,
        )

        val stored = local.observeCustomByCar(carId).first()

        assertEquals(listOf("rem-1"), stored.map { it.id.value })
    }

    @Test
    fun anUnknownPresetAloneDoesNotSkipTheRow() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(reminder())
        db.reminderQueries.updateReminder(
            dueDate = "2026-08-10",
            title = "Air pressure check",
            isPaused = 0,
            startsOn = "2026-08-10",
            remindAt = "09:00",
            repeatKind = REPEAT_EVERY_DAYS,
            repeatEveryDays = 15,
            repeatEveryKm = null,
            anchorKm = null,
            preset = "PRESET_FROM_THE_FUTURE",
            updatedAt = "2026-08-06T11:00:00Z",
            syncStatus = SyncStatus.PENDING.name,
            id = "rem-1",
        )

        val stored = local.observeCustomByCar(carId).first().single()
        assertNull(stored.preset)
    }
}
