package com.hopcape.odo.infrastructure.database.car

import com.hopcape.odo.core.domain.car.lookup.VehicleSource
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.inMemoryDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * Answering "is this your car?" from cars this device already holds (issue #392).
 *
 * What is checked here is which row wins and which rows are invisible — the parts a query
 * gets subtly wrong. A soft-deleted car answering would resurrect a car the owner removed,
 * and another owner's row answering would be a data leak in a table RLS protects on the
 * server but nothing protects locally.
 */
class CarRegistrationLookupTest {

    @Test
    fun aStoredPlateAnswersWithItsVehicle() = runTest {
        val (db, _) = inMemoryDatabase()
        db.insertCar(id = "car-1", model = "Swift", variant = "VXI")

        val vehicle = source(db).vehicleByRegistration(OwnerId(OWNER), PLATE)

        assertEquals("Maruti Suzuki", vehicle?.make)
        assertEquals("Swift", vehicle?.model)
        assertEquals("VXI", vehicle?.variant)
        assertEquals(2019, vehicle?.year?.value)
        assertEquals(FuelType.PETROL, vehicle?.fuelType)
        assertEquals(VehicleSource.OWN_RECORD, vehicle?.source)
    }

    @Test
    fun aReAddedCarAnswersOverTheTombstoneOfTheOldOne() = runTest {
        // The only way one owner holds two rows for a plate: uq_cars_owner_reg is partial,
        // so removing a car frees its plate to be added again. The live row is the answer,
        // and the removed one must not surface a trim the owner already corrected.
        val (db, _) = inMemoryDatabase()
        db.insertCar(id = "removed", variant = "LXI", createdAt = EARLY, updatedAt = LATE, deletedAt = LATE)
        db.insertCar(id = "re-added", variant = "ZXI", createdAt = LATEST, updatedAt = LATEST)

        assertEquals("ZXI", source(db).vehicleByRegistration(OwnerId(OWNER), PLATE)?.variant)
    }

    @Test
    fun aRemovedCarDoesNotAnswer() = runTest {
        val (db, _) = inMemoryDatabase()
        db.insertCar(id = "gone", deletedAt = LATE)

        assertNull(source(db).vehicleByRegistration(OwnerId(OWNER), PLATE))
    }

    @Test
    fun anotherOwnersCarDoesNotAnswer() = runTest {
        val (db, _) = inMemoryDatabase()
        db.insertCar(id = "theirs", owner = "owner-2")

        assertNull(source(db).vehicleByRegistration(OwnerId(OWNER), PLATE))
    }

    @Test
    fun anUnknownPlateAnswersNothing() = runTest {
        val (db, _) = inMemoryDatabase()
        db.insertCar(id = "car-1")

        val other = RegistrationNumber.of("DL8CAF5031")!!
        assertNull(source(db).vehicleByRegistration(OwnerId(OWNER), other))
    }

    private fun source(db: OdoDatabase) = SqlDelightCarLocalDataSource(db)

    private fun OdoDatabase.insertCar(
        id: String,
        owner: String = OWNER,
        model: String = "Swift",
        variant: String? = "VXI",
        plate: String? = PLATE.value,
        createdAt: String = EARLY,
        updatedAt: String = EARLY,
        deletedAt: String? = null,
    ) = carQueries.insertCar(
        id, owner, "Maruti Suzuki", model, variant, 2019, "PETROL", plate,
        42_000, null, null, 0, updatedAt, createdAt, updatedAt, deletedAt, null, "PENDING",
    )

    private companion object {
        const val OWNER = "owner-1"
        val PLATE = RegistrationNumber.of("MH01AB1234")!!

        val EARLY = Instant.parse("2026-01-01T10:00:00Z").toString()
        val LATE = Instant.parse("2026-06-01T10:00:00Z").toString()
        val LATEST = Instant.parse("2026-08-01T10:00:00Z").toString()
    }
}
