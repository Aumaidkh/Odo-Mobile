package com.hopcape.odo.core.data.car

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
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

    private fun newDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private fun repo(db: OdoDatabase) =
        CarRepositoryImpl(database = db, dispatcher = Dispatchers.Unconfined)

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
}
