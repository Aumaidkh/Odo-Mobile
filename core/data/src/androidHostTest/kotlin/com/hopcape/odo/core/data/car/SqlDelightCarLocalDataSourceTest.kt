package com.hopcape.odo.core.data.car

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightCarLocalDataSourceTest {

    private val ownerId = OwnerId("owner-1")

    private lateinit var driver: JdbcSqliteDriver

    private fun newDb(): OdoDatabase {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private fun local(db: OdoDatabase) = SqlDelightCarLocalDataSource(database = db, dispatcher = Dispatchers.Unconfined)

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
        val local = local(db)

        local.insert(car("car-1"))

        val primary = local.observePrimary().first()
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
        val local = local(db)
        local.insert(car("car-1"))
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

        assertTrue(local.update(edited))

        val stored = local.observePrimary().first()
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
        val local = local(db)
        local.insert(car("car-1"))
        val createdAt = db.carQueries.selectById("car-1").executeAsOne().created_at

        assertTrue(local.update(car("car-1", isPrimary = true)))

        assertEquals(createdAt, db.carQueries.selectById("car-1").executeAsOne().created_at)
    }

    @Test
    fun update_unknownCar_answersFalse() = runTest {
        assertFalse(local(newDb()).update(car("ghost")))
    }

    @Test
    fun insertingSecondPrimary_demotesTheFirst() = runTest {
        val db = newDb()
        val local = local(db)

        local.insert(car("car-1"))
        local.insert(car("car-2", registration = "dl 01 cd 5678"))

        val primary = local.observePrimary().first()
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
        val local = local(db)
        local.insert(car("car-1"))
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
        assertTrue(local.update(renamed))

        assertEquals(stampedOnInsert, db.carQueries.selectById("car-1").executeAsOne().odometer_updated_at)
    }

    @Test
    fun update_redatesTheOdometerWhenTheReadingMoves() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(car("car-1"))
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
        assertTrue(local.update(moved))

        val stamp = db.carQueries.selectById("car-1").executeAsOne().odometer_updated_at
        assertNotNull(stamp)
        assertTrue(stamp > "2026-01-01T00:00:00Z", "expected a fresh stamp but was $stamp")
    }

    @Test
    fun observeById_readsBackTheCarThatWasAskedFor() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(car("car-1"))
        local.insert(car("car-2", registration = "dl 01 cd 5678"))

        // car-2 is primary now, so this only passes if the id is what is read.
        val car = local.observeById(CarId("car-1")).first()

        assertEquals("car-1", car?.id?.value)
    }

    @Test
    fun observeById_unknownCar_emitsNull() = runTest {
        assertNull(local(newDb()).observeById(CarId("ghost")).first())
    }

    @Test
    fun softDelete_tombstonesTheCarAndItsLogsAndDocuments() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(car("car-1"))
        db.insertLog("log-1", "car-1")
        db.insertDoc("doc-1", "car-1")

        local.softDelete(CarId("car-1"))

        // Gone from every read path, but still present as a row.
        assertNull(local.observeById(CarId("car-1")).first())
        assertNull(local.observePrimary().first())
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
        val local = local(db)
        local.insert(car("car-1"))
        // Both children start SYNCED, so the reset back to PENDING is observable.
        db.insertLog("log-1", "car-1")
        db.insertDoc("doc-1", "car-1")

        local.softDelete(CarId("car-1"))

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
        val local = local(db)
        local.insert(car("car-1"))
        local.insert(car("car-2", registration = "dl 01 cd 5678"))
        db.insertLog("log-1", "car-1")
        db.insertLog("log-2", "car-2")
        db.insertDoc("doc-2", "car-2")

        local.softDelete(CarId("car-1"))

        assertNotNull(local.observeById(CarId("car-2")).first())
        assertNotNull(db.serviceLogQueries.selectById("log-2").executeAsOneOrNull())
        assertNotNull(db.documentQueries.selectById("doc-2").executeAsOneOrNull())
    }

    /** Asking for a car that is already gone is what the caller wanted, not a failure. */
    @Test
    fun softDelete_unknownCar_doesNotThrow() = runTest {
        local(newDb()).softDelete(CarId("ghost"))
    }
}
