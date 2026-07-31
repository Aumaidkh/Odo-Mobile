package com.hopcape.odo.core.data.servicelog

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import arrow.core.getOrElse
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
import com.hopcape.odo.core.domain.fairness.model.FairnessQueryItem
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.fairness.model.FairnessSnapshot
import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ServiceLogRepositoryImplTest {

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")

    /* ------------------------- fixtures ------------------------- */

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

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    /** Records what the repository asked the scheduler for, and when. */
    private class RecordingScheduler : SyncScheduler {
        val requested = mutableListOf<SyncReason>()
        var startupRuns = 0
        override fun scheduleStartupSync() { startupRuns++ }
        override fun requestSync(reason: SyncReason) { requested += reason }
    }

    private fun newDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private fun repo(
        db: OdoDatabase,
        crash: CrashRecorder = RecordingCrash(),
        now: String = "2026-07-30T10:00:00Z",
        scheduler: SyncScheduler = RecordingScheduler(),
    ) = ServiceLogRepositoryImpl(
        database = db,
        telemetry = DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = crash),
        scheduler = scheduler,
        remote = FakeServiceLogRemoteDataSource(),
        clock = FixedClock(Instant.parse(now)),
        dispatcher = Dispatchers.Unconfined,
    )

    /** A car row is the odometer timeline's baseline, so most tests need one. */
    private fun OdoDatabase.seedCar(odometerKm: Long = 45_000, createdAt: String = "2026-01-01T09:00:00Z") {
        carQueries.insertCar(
            id = carId.value,
            owner_id = ownerId.value,
            make = "Maruti Suzuki",
            model = "Swift",
            variant = null,
            year = 2020,
            fuel_type = "PETROL",
            registration_number = null,
            current_odometer_km = odometerKm,
            purchase_year = null,
            nickname = null,
            is_primary = 1,
            created_at = createdAt,
            updated_at = createdAt,
            deleted_at = null,
            remote_version = null,
            sync_status = SyncStatus.PENDING.name,
        )
    }

    private fun amt(paise: Long) = Amount.of(paise).getOrElse { Amount.ZERO }

    private fun entry(
        id: String = "log-1",
        day: Int = 15,
        odometerKm: Int = 50_000,
        totalPaise: Long = 330_000,
        categories: Set<ServiceCategory> = setOf(ServiceCategory.BRAKES),
        fairness: FairnessSnapshot? = null,
        billPhotoRef: String? = null,
    ) = ServiceLogEntry.reconstitute(
        id = ServiceLogId(id),
        carId = carId,
        ownerId = ownerId,
        serviceDate = LocalDate(2026, 6, day),
        odometerKm = odometerKm,
        totalAmountPaise = totalPaise,
        workshopName = "Sharma Motors",
        notes = "front pads",
        source = LogSource.MANUAL,
        billId = null,
        categories = categories,
        billPhotoRef = billPhotoRef,
        fairness = fairness,
    )

    private fun snapshot(paidPaise: Long, averagePaise: Long, sampleSize: Int = 30) = FairnessSnapshot(
        report = FairnessReport.of(
            query = FairnessQuery(
                city = "Pune",
                items = listOf(FairnessQueryItem("Front pads", ServiceCategory.BRAKES, amt(paidPaise))),
            ),
            estimates = mapOf(
                ServiceCategory.BRAKES to FairnessEstimate(ServiceCategory.BRAKES, "Pune", amt(averagePaise), sampleSize),
            ),
        ),
        checkedAt = Instant.parse("2026-07-03T10:00:00Z"),
    )

    /* ------------------------- round trip ------------------------- */

    @Test
    fun addedEntry_comesBackWithEveryField() = runTest {
        val db = newDb().apply { seedCar() }
        val repo = repo(db)

        assertTrue(repo.add(entry()).isRight())

        val stored = repo.observe(carId).first().single()
        assertEquals("log-1", stored.id.value)
        assertEquals(LocalDate(2026, 6, 15), stored.serviceDate)
        assertEquals(50_000, stored.odometer.km)
        assertEquals(330_000L, stored.totalAmount.paise)
        assertEquals("Sharma Motors", stored.workshopName?.value)
        assertEquals("front pads", stored.notes?.value)
        assertEquals(setOf(ServiceCategory.BRAKES), stored.categories)
    }

    @Test
    fun categories_roundTripAsASet() = runTest {
        val db = newDb().apply { seedCar() }
        val repo = repo(db)
        val tags = setOf(ServiceCategory.BRAKES, ServiceCategory.OIL_CHANGE, ServiceCategory.AC)

        repo.add(entry(categories = tags))

        assertEquals(tags, repo.observe(carId).first().single().categories)
    }

    @Test
    fun anEntryWithNoCategories_readsBackEmptyRatherThanFailing() = runTest {
        val db = newDb().apply { seedCar() }
        val repo = repo(db)

        val added = repo.add(entry(categories = emptySet()))
        assertTrue(added.isRight(), "add failed: $added")

        assertEquals(emptySet(), repo.observe(carId).first().single().categories)
    }

    @Test
    fun fairnessSnapshot_survivesTheRoundTrip() = runTest {
        val db = newDb().apply { seedCar() }
        val repo = repo(db)

        repo.add(entry(fairness = snapshot(paidPaise = 330_000, averagePaise = 240_000)))

        val stored = assertNotNull(repo.observe(ServiceLogId("log-1")).first()?.fairness)
        // The verdict is not stored — it is rebuilt from the frozen estimate, and must come
        // back identical.
        val over = assertIs<FairnessVerdict.Over>(stored.verdict)
        assertEquals(90_000L, over.by.paise)
        assertEquals(240_000L, stored.report.items.single().cityAverage?.paise)
        assertEquals(30, stored.report.sampleSize)
        assertEquals(Instant.parse("2026-07-03T10:00:00Z"), stored.checkedAt)
    }

    @Test
    fun entriesComeBackNewestFirst() = runTest {
        val db = newDb().apply { seedCar() }
        val repo = repo(db)

        repo.add(entry(id = "old", day = 1, odometerKm = 46_000))
        repo.add(entry(id = "new", day = 20, odometerKm = 52_000))

        assertEquals(listOf("new", "old"), repo.observe(carId).first().map { it.id.value })
    }

    /* ------------------------- writes ------------------------- */

    @Test
    fun writesLandPending() = runTest {
        val db = newDb().apply { seedCar() }
        repo(db).add(entry())

        val row = db.serviceLogQueries.selectById("log-1").executeAsOne()
        assertEquals(SyncStatus.PENDING.name, row.sync_status)
        assertEquals("2026-07-30T10:00:00Z", row.created_at)
    }

    @Test
    fun anEdit_rewritesCategoriesAndStampsTheParent() = runTest {
        val db = newDb().apply { seedCar() }
        val repo = repo(db)
        repo.add(entry(categories = setOf(ServiceCategory.BRAKES)))

        // Mark the row SYNCED so the edit's return to PENDING is observable.
        db.serviceLogQueries.updateServiceLog(
            serviceDate = "2026-06-15", odometerKm = 50_000, totalAmountPaise = 330_000,
            workshopName = "Sharma Motors", notes = "front pads", source = "MANUAL",
            billId = null, billPhotoPath = null, fairnessSnapshot = null,
            updatedAt = "2026-07-29T00:00:00Z", syncStatus = SyncStatus.SYNCED.name, id = "log-1",
        )

        val edited = repo(db, now = "2026-07-31T08:00:00Z")
            .update(entry(categories = setOf(ServiceCategory.OIL_CHANGE, ServiceCategory.AC)))

        assertTrue(edited.isRight(), "expected Right but was $edited")
        val stored = repo.observe(ServiceLogId("log-1")).first()
        assertEquals(setOf(ServiceCategory.OIL_CHANGE, ServiceCategory.AC), stored?.categories)

        // A tag-only change still has to reach the server, so the parent goes back to PENDING.
        val row = db.serviceLogQueries.selectById("log-1").executeAsOne()
        assertEquals(SyncStatus.PENDING.name, row.sync_status)
        assertEquals("2026-07-31T08:00:00Z", row.updated_at)
    }

    @Test
    fun updatingAMissingEntry_isServiceLogNotFound() = runTest {
        val db = newDb().apply { seedCar() }

        val result = repo(db).update(entry(id = "ghost"))

        assertEquals(DomainError.ServiceLogNotFound, result.leftOrNull())
    }

    @Test
    fun softDelete_hidesTheEntryFromBothObservers() = runTest {
        val db = newDb().apply { seedCar() }
        val repo = repo(db)
        repo.add(entry())

        assertTrue(repo.softDelete(ServiceLogId("log-1")).isRight())

        assertEquals(emptyList(), repo.observe(carId).first())
        assertNull(repo.observe(ServiceLogId("log-1")).first())
        // The tombstone survives, PENDING, so the deletion itself can sync.
        val row = db.serviceLogQueries.selectById("log-1").executeAsOneOrNull()
        assertNull(row, "the query filters tombstones")
    }

    /* ------------------------- odometer timeline ------------------------- */

    @Test
    fun odometerReadings_coalesceTheCarBaselineWithItsLogs() = runTest {
        val db = newDb().apply { seedCar(odometerKm = 45_000, createdAt = "2026-01-01T09:00:00Z") }
        val repo = repo(db)
        repo.add(entry(id = "log-1", day = 15, odometerKm = 50_000))

        val readings = assertNotNull(repo.odometerReadings(carId))

        assertEquals(2, readings.size)
        val baseline = readings.single { it.logId == null }
        assertEquals(45_000, baseline.odometer.km)
        // The car's instant is truncated to the date the timeline compares on.
        assertEquals(LocalDate(2026, 1, 1), baseline.date)
        val log = readings.single { it.logId != null }
        assertEquals(50_000, log.odometer.km)
        assertEquals(LocalDate(2026, 6, 15), log.date)
    }

    @Test
    fun odometerReadings_excludeDeletedEntries() = runTest {
        val db = newDb().apply { seedCar() }
        val repo = repo(db)
        repo.add(entry(id = "log-1", odometerKm = 50_000))
        repo.softDelete(ServiceLogId("log-1"))

        val readings = assertNotNull(repo.odometerReadings(carId))

        assertEquals(1, readings.size, "only the car baseline should remain")
        assertNull(readings.single().logId)
    }

    @Test
    fun odometerReadings_areNullForAnUnknownCar() = runTest {
        val db = newDb()

        assertNull(repo(db).odometerReadings(CarId("nope")))
    }

    /* ------------------------- failure reporting ------------------------- */

    @Test
    fun aFailedWrite_isRecordedAsANonFatal() = runTest {
        val db = newDb().apply { seedCar() }
        val crash = RecordingCrash()
        val repo = repo(db, crash = crash)
        repo.add(entry())

        // Same primary key twice — the insert throws, and the caller only ever sees a Left.
        val result = repo.add(entry())

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
        assertEquals(1, crash.nonFatals.size, "a swallowed exception must still reach the dashboard")
    }

    /* ------------------------- sync is asked for ------------------------- */

    @Test
    fun deletingAnEntry_asksForASync() = runTest {
        // The one that is easiest to miss: a deletion's whole payload is the tombstone, so
        // without this ask the row sits PENDING forever and no other device ever learns it
        // went.
        val db = newDb().apply { seedCar() }
        val scheduler = RecordingScheduler()
        val repo = repo(db, scheduler = scheduler)
        repo.add(entry())
        scheduler.requested.clear()

        repo.softDelete(ServiceLogId("log-1"))

        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun everyWrite_asksForASync() = runTest {
        val db = newDb().apply { seedCar() }
        val scheduler = RecordingScheduler()
        val repo = repo(db, scheduler = scheduler)

        repo.add(entry())
        repo.update(entry(odometerKm = 51_000))
        repo.softDelete(ServiceLogId("log-1"))

        // Three writes, three asks — coalescing them is the scheduler's job, not ours.
        assertEquals(List(3) { SyncReason.LocalWrite }, scheduler.requested)
    }

    @Test
    fun aFailedWrite_doesNotAskForASync() = runTest {
        val db = newDb().apply { seedCar() }
        val scheduler = RecordingScheduler()

        // No such entry: the update never happens, so there is nothing to push.
        repo(db, scheduler = scheduler).update(entry(id = "ghost"))

        assertTrue(scheduler.requested.isEmpty(), "a rejected write left nothing PENDING")
    }

    @Test
    fun aSchedulerThatThrows_doesNotFailTheWrite() = runTest {
        val db = newDb().apply { seedCar() }
        val exploding = object : SyncScheduler {
            override fun scheduleStartupSync() = Unit
            override fun requestSync(reason: SyncReason) = error("no WorkManager here")
        }

        val result = repo(db, scheduler = exploding).add(entry())

        // The data is safely local and PENDING; the next trigger will carry it.
        assertTrue(result.isRight(), "scheduling is not part of the write's success: $result")
        assertEquals(1, repo(db).observe(carId).first().size)
    }
}
