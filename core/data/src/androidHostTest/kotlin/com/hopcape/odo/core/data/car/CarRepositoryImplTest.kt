package com.hopcape.odo.core.data.car

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
        assertEquals(SyncStatus.PENDING, row.sync_status)
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
