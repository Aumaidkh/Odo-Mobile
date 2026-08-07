package com.hopcape.odo.core.data.car

import com.hopcape.odo.core.data.sync.silentSyncTelemetry
import com.hopcape.odo.core.data.car.FakeCarRemoteDataSource
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncRunner
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CarRepositoryImplTest {

    private val ownerId = OwnerId("owner-1")

    private lateinit var driver: JdbcSqliteDriver

    private fun newDb(): OdoDatabase {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    /** What a soft delete left behind. Both fields are `deleted_at`-blind by design. */
    private data class Tombstone(val deletedAt: String?, val syncStatus: String)

    /**
     * Reads a row straight from the driver, because every generated query hides deleted
     * rows — and the tombstone is the thing under test.
     */
    private fun tombstoneOf(table: String, id: String): Tombstone? = driver.executeQuery(
        identifier = null,
        sql = "SELECT deleted_at, sync_status FROM $table WHERE id = ?",
        mapper = { cursor ->
            QueryResult.Value(
                if (cursor.next().value) Tombstone(cursor.getString(0), cursor.getString(1)!!) else null,
            )
        },
        parameters = 1,
    ) { bindString(0, id) }.value

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

    private fun repo(db: OdoDatabase, scheduler: SyncScheduler = RecordingScheduler()) =
        CarRepositoryImpl(
            local = SqlDelightCarLocalDataSource(database = db, dispatcher = Dispatchers.Unconfined),
            telemetry = DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = NoopCrash),
            scheduler = scheduler,
            runner = SyncRunner(
                entity = SyncEntity.CARS,
                table = CarSyncTable(
                    database = db,
                    remote = FakeCarRemoteDataSource(),
                    telemetry = silentSyncTelemetry(),
                    ownerId = { null },
                ),
                database = db,
                telemetry = silentSyncTelemetry(),
            ),
        )

    /** A service log for [carId], written straight to the table — the domain path is not what is under test. */
    private fun OdoDatabase.insertLog(id: String, carId: String) = serviceLogQueries.insertServiceLog(
        id = id,
        carId = carId,
        ownerId = ownerId.value,
        serviceDate = "2026-03-02",
        odometerKm = 44_000,
        totalAmountPaise = 280_000,
        workshopName = null,
        notes = null,
        source = "MANUAL",
        billId = null,
        billPhotoPath = null,
        fairnessSnapshot = null,
        now = "2026-03-02T10:00:00Z",
        syncStatus = SyncStatus.SYNCED.name,
    )

    /** A document for [carId], written straight to the table. */
    private fun OdoDatabase.insertDoc(id: String, carId: String) = documentQueries.insertDocument(
        id = id,
        carId = carId,
        ownerId = ownerId.value,
        docType = "INSURANCE",
        title = null,
        storagePath = "documents/$carId/$id.pdf",
        docSource = "UPLOADED",
        issuedDate = null,
        expiryDate = "2027-07-03",
        now = "2026-03-02T10:00:00Z",
        syncStatus = SyncStatus.SYNCED.name,
    )

    private fun car(
        id: String,
        registration: String? = "mh 12 ab 1234",
        isPrimary: Boolean = true,
    ): Car = Car.create(
        id = CarId(id),
        ownerId = ownerId,
        make = "Maruti Suzuki",
        model = "Swift",
        year = 2020,
        fuelType = FuelType.PETROL,
        odometerKm = 45_000,
        registrationNumber = registration,
        purchaseYear = 2021,
        isPrimary = isPrimary,
    ).getOrNull()!!

    @Test
    fun insert_thenObservePrimary_readsBackWithNormalizedRegAndPendingSync() = runTest {
        val db = newDb()
        val repo = repo(db)

        val result = repo.add(car("car-1"))
        assertTrue(result.isRight(), "expected Right but was $result")

        val primary = repo.observePrimaryCar().first()
        assertNotNull(primary)
        assertEquals("car-1", primary.id.value)
        // Registration persisted normalized (uppercase, no spaces).
        assertEquals("MH12AB1234", primary.registrationNumber?.value)

        // sync_status groundwork lands as PENDING (not part of the domain Car).
        val row = db.carQueries.selectById("car-1").executeAsOne()
        assertEquals(SyncStatus.PENDING.name, row.sync_status)
        assertNull(row.remote_version)
    }

    @Test
    fun update_editsTheStoredCarAndReturnsItToPending() = runTest {
        val db = newDb()
        val repo = repo(db)
        assertTrue(repo.add(car("car-1")).isRight())
        // Pretend it already reached the server, so the reset back to PENDING is
        // actually observable.
        db.carQueries.updateCar(
            make = "Maruti Suzuki",
            model = "Swift",
            variant = null,
            year = 2020,
            fuelType = FuelType.PETROL.name,
            registrationNumber = "MH12AB1234",
            odometerKm = 45_000,
            purchaseYear = 2021,
            nickname = null,
            isPrimary = 1L,
            odometerUpdatedAt = "2026-07-30T10:00:00Z",
            updatedAt = "2026-07-30T10:00:00Z",
            syncStatus = SyncStatus.SYNCED.name,
            id = "car-1",
        )

        val edited = Car.create(
            id = CarId("car-1"),
            ownerId = ownerId,
            make = "Maruti Suzuki",
            model = "Swift",
            year = 2020,
            fuelType = FuelType.PETROL,
            odometerKm = 61_500,
            registrationNumber = "mh 12 ab 1234",
            purchaseYear = 2021,
            nickname = "Chhoti",
            isPrimary = true,
        ).getOrNull()!!

        assertTrue(repo.update(edited).isRight())

        val stored = repo.observePrimaryCar().first()
        assertEquals(61_500, stored?.odometer?.km)
        assertEquals("Chhoti", stored?.nickname)

        val row = db.carQueries.selectById("car-1").executeAsOne()
        assertEquals(SyncStatus.PENDING.name, row.sync_status)
        // An edit must not create a second car.
        assertEquals(1, db.carQueries.selectPrimaryCar().executeAsList().size)
    }

    @Test
    fun update_preservesCreatedAt() = runTest {
        val db = newDb()
        val repo = repo(db)
        assertTrue(repo.add(car("car-1")).isRight())
        val createdAt = db.carQueries.selectById("car-1").executeAsOne().created_at

        assertTrue(repo.update(car("car-1", isPrimary = true)).isRight())

        assertEquals(createdAt, db.carQueries.selectById("car-1").executeAsOne().created_at)
    }

    @Test
    fun update_unknownCar_isCarNotFound() = runTest {
        val result = repo(newDb()).update(car("ghost"))

        assertIs<DomainError.CarNotFound>(result.leftOrNull())
    }

    @Test
    fun insertingSecondPrimary_demotesTheFirst() = runTest {
        val db = newDb()
        val repo = repo(db)

        assertTrue(repo.add(car("car-1")).isRight())
        assertTrue(repo.add(car("car-2", registration = "dl 01 cd 5678")).isRight())

        val primary = repo.observePrimaryCar().first()
        assertEquals("car-2", primary?.id?.value)

        // The first car is demoted, not deleted.
        val first = db.carQueries.selectById("car-1").executeAsOne()
        assertEquals(0L, first.is_primary)
    }

    /**
     * The odometer timeline reads the car's own reading as a dated entry. Re-dating it on
     * an unrelated edit would claim the odometer was checked when it wasn't, and every
     * backdated service log after that would be measured against a date that never happened.
     */
    @Test
    fun update_keepsTheOdometerDateWhenTheReadingIsUnchanged() = runTest {
        val db = newDb()
        val repo = repo(db)
        assertTrue(repo.add(car("car-1")).isRight())
        val stampedOnInsert = db.carQueries.selectById("car-1").executeAsOne().odometer_updated_at

        // Same reading, different nickname.
        val renamed = Car.create(
            id = CarId("car-1"),
            ownerId = ownerId,
            make = "Maruti Suzuki",
            model = "Swift",
            year = 2020,
            fuelType = FuelType.PETROL,
            odometerKm = 45_000,
            registrationNumber = "mh 12 ab 1234",
            purchaseYear = 2021,
            nickname = "Chhoti",
            isPrimary = true,
        ).getOrNull()!!
        assertTrue(repo.update(renamed).isRight())

        assertEquals(stampedOnInsert, db.carQueries.selectById("car-1").executeAsOne().odometer_updated_at)
    }

    @Test
    fun update_redatesTheOdometerWhenTheReadingMoves() = runTest {
        val db = newDb()
        val repo = repo(db)
        assertTrue(repo.add(car("car-1")).isRight())
        // Backdate the stamp so a fresh one is unmistakable.
        db.carQueries.updateCar(
            make = "Maruti Suzuki",
            model = "Swift",
            variant = null,
            year = 2020,
            fuelType = FuelType.PETROL.name,
            registrationNumber = "MH12AB1234",
            odometerKm = 45_000,
            purchaseYear = 2021,
            nickname = null,
            isPrimary = 1L,
            odometerUpdatedAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
            syncStatus = SyncStatus.SYNCED.name,
            id = "car-1",
        )

        val moved = Car.create(
            id = CarId("car-1"),
            ownerId = ownerId,
            make = "Maruti Suzuki",
            model = "Swift",
            year = 2020,
            fuelType = FuelType.PETROL,
            odometerKm = 61_500,
            registrationNumber = "mh 12 ab 1234",
            purchaseYear = 2021,
            isPrimary = true,
        ).getOrNull()!!
        assertTrue(repo.update(moved).isRight())

        val stamp = db.carQueries.selectById("car-1").executeAsOne().odometer_updated_at
        assertNotNull(stamp)
        assertTrue(stamp > "2026-01-01T00:00:00Z", "expected a fresh stamp but was $stamp")
    }

    @Test
    fun observeById_readsBackTheCarThatWasAskedFor() = runTest {
        val db = newDb()
        val repo = repo(db)
        assertTrue(repo.add(car("car-1")).isRight())
        assertTrue(repo.add(car("car-2", registration = "dl 01 cd 5678")).isRight())

        // car-2 is primary now, so this only passes if the id is what is read.
        val car = repo.observe(CarId("car-1")).first()

        assertEquals("car-1", car?.id?.value)
    }

    @Test
    fun observeById_unknownCar_emitsNull() = runTest {
        assertNull(repo(newDb()).observe(CarId("ghost")).first())
    }

    @Test
    fun softDelete_tombstonesTheCarAndItsLogsAndDocuments() = runTest {
        val db = newDb()
        val repo = repo(db)
        assertTrue(repo.add(car("car-1")).isRight())
        db.insertLog("log-1", "car-1")
        db.insertDoc("doc-1", "car-1")

        val result = repo.softDelete(CarId("car-1"))
        assertTrue(result.isRight(), "expected Right but was $result")

        // Gone from every read path, but still present as a row.
        assertNull(repo.observe(CarId("car-1")).first())
        assertNull(repo.observePrimaryCar().first())
        assertNull(db.carQueries.selectById("car-1").executeAsOneOrNull())

        assertNull(
            db.serviceLogQueries.selectById("log-1").executeAsOneOrNull(),
            "the car's service log should be tombstoned too",
        )
        assertNull(
            db.documentQueries.selectById("doc-1").executeAsOneOrNull(),
            "the car's document should be tombstoned too",
        )
    }

    @Test
    fun softDelete_sendsEveryTombstoneBackToPending() = runTest {
        val db = newDb()
        val repo = repo(db)
        assertTrue(repo.add(car("car-1")).isRight())
        // Both children start SYNCED, so the reset back to PENDING is observable.
        db.insertLog("log-1", "car-1")
        db.insertDoc("doc-1", "car-1")

        assertTrue(repo.softDelete(CarId("car-1")).isRight())

        listOf("cars" to "car-1", "service_logs" to "log-1", "documents" to "doc-1")
            .forEach { (table, id) ->
                val row = assertNotNull(tombstoneOf(table, id), "$table row $id vanished")
                assertNotNull(row.deletedAt, "$table row $id should carry a deleted_at")
                assertEquals(SyncStatus.PENDING.name, row.syncStatus, "$table row $id")
            }
    }

    @Test
    fun softDelete_leavesAnotherCarsRowsAlone() = runTest {
        val db = newDb()
        val repo = repo(db)
        assertTrue(repo.add(car("car-1")).isRight())
        assertTrue(repo.add(car("car-2", registration = "dl 01 cd 5678")).isRight())
        db.insertLog("log-1", "car-1")
        db.insertLog("log-2", "car-2")
        db.insertDoc("doc-2", "car-2")

        assertTrue(repo.softDelete(CarId("car-1")).isRight())

        assertNotNull(repo.observe(CarId("car-2")).first())
        assertNotNull(db.serviceLogQueries.selectById("log-2").executeAsOneOrNull())
        assertNotNull(db.documentQueries.selectById("doc-2").executeAsOneOrNull())
    }

    /** Asking for a car that is already gone is what the caller wanted, not a failure. */
    @Test
    fun softDelete_unknownCar_succeeds() = runTest {
        assertTrue(repo(newDb()).softDelete(CarId("ghost")).isRight())
    }

    @Test
    fun softDelete_asksForASync() = runTest {
        val db = newDb()
        val scheduler = RecordingScheduler()
        val repo = repo(db, scheduler)
        assertTrue(repo.add(car("car-1")).isRight())
        scheduler.requested.clear()

        assertTrue(repo.softDelete(CarId("car-1")).isRight())

        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }
}
