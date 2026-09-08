package com.hopcape.odo.infrastructure.database.car

import com.hopcape.odo.core.data.car.CarDto
import com.hopcape.odo.core.data.car.CarRemoteDataSource
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.inMemoryDatabase
import com.hopcape.odo.infrastructure.database.sync.silentSyncTelemetry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Signing in and finding the account already has this car's history (issue #425).
 *
 * Both ways it happens are here: the owner re-adds their car and the plate matches one the
 * account holds, or the pull simply brings a car down that this device has never seen. Both
 * used to be silent — the garage filled with records the owner never entered on this phone
 * and nothing said where they came from.
 */
class CarRestoredHistoryTest {

    private val owner = "owner-1"
    private val plate = "MH01AB1234"
    private val earlier = Instant.parse("2026-01-01T10:00:00Z").toString()

    @Test
    fun aReAddedCarThatMatchesTheAccountsHistoryIsRecordedForTheOwnerToBeTold() = runTest {
        val (db, _) = inMemoryDatabase()
        db.insertLocalCar(id = "local-1")
        val restored = RecordingRestoredHistoryStore()
        val table = table(db, server = listOf(serverCar(id = "server-1")), restored = restored)

        table.reconcileBeforePush(table.pending())

        // Under the server's id, because that is the car the pull is about to fill.
        assertEquals(listOf(CarId("server-1")), restored.recorded)
    }

    @Test
    fun aCarPulledDownForTheFirstTimeIsRecordedToo() = runTest {
        val (db, _) = inMemoryDatabase()
        val restored = RecordingRestoredHistoryStore()
        val table = table(db, server = emptyList(), restored = restored)

        table.afterPull(insertedIds = listOf("server-1"))

        // A fresh install that signs in never goes through the plate match — the car simply
        // arrives. Equally a restore, equally worth saying.
        assertEquals(listOf(CarId("server-1")), restored.recorded)
    }

    /**
     * The fallback the guard must not break: a device that named no car of its own still gets
     * told about the history that arrived. Inserted through [CarSyncTable.applyRemote] first,
     * so the plated-car lookup sees what a real pull would leave it.
     */
    @Test
    fun aFreshInstallStillAnnouncesTheHistoryItPulledDown() = runTest {
        val (db, _) = inMemoryDatabase()
        val restored = RecordingRestoredHistoryStore()
        val table = table(db, server = emptyList(), restored = restored)

        table.applyRemote(serverCar(id = "server-1"))
        table.afterPull(insertedIds = listOf("server-1"))

        assertEquals(listOf(CarId("server-1")), restored.recorded)
    }

    /**
     * The bug this guards (#456). The account holds more than one car, so the pull inserts the
     * others alongside the one the owner named — and every insert used to overwrite the
     * announcement, leaving the sheet describing a car the owner never entered.
     */
    @Test
    fun aCarTheAccountHoldsIsNotAnnouncedOverTheCarTheOwnerEntered() = runTest {
        val (db, _) = inMemoryDatabase()
        db.insertLocalCar(id = "local-1")
        val restored = RecordingRestoredHistoryStore()
        val table = table(db, server = listOf(serverCar(id = "server-1")), restored = restored)

        // The push matches the plate and announces the owner's own car.
        table.reconcileBeforePush(table.pending())
        // The pull then brings down another car the account happens to hold.
        table.applyRemote(serverCar(id = "server-other", plate = "JK03Q8279"))
        table.afterPull(insertedIds = listOf("server-other"))

        assertEquals(
            listOf(CarId("server-1")),
            restored.recorded,
            "the account's other car must not replace the owner's own",
        )
    }

    @Test
    fun aCarThisDeviceAddedItselfIsNotAnnouncedAsRestored() = runTest {
        val (db, _) = inMemoryDatabase()
        db.insertLocalCar(id = "local-1")
        val restored = RecordingRestoredHistoryStore()
        // Nothing on the server carries this plate, so the owner really is adding a new car.
        val table = table(db, server = listOf(serverCar(id = "server-1", plate = "MH01ZZ9999")), restored = restored)

        table.reconcileBeforePush(table.pending())

        assertTrue(restored.recorded.isEmpty())
    }

    /* ------------------------------ scaffolding ------------------------------ */

    private fun table(db: OdoDatabase, server: List<CarDto>, restored: RecordingRestoredHistoryStore) =
        CarSyncTable(
            database = db,
            remote = StubRemote(server),
            telemetry = silentSyncTelemetry(),
            ownerId = { owner },
            restored = restored,
        )

    private fun serverCar(id: String, plate: String = this.plate) = CarDto(
        id = id,
        ownerId = owner,
        make = "Maruti",
        model = "Swift",
        year = 2019,
        fuelType = "petrol",
        registrationNumber = plate,
        currentOdometerKm = 41_000,
        isPrimary = true,
        createdAt = earlier,
        updatedAt = earlier,
        deletedAt = null,
    )

    private fun OdoDatabase.insertLocalCar(id: String) = carQueries.insertCar(
        id, owner, "Maruti", "Swift", null, 2019, "PETROL", plate,
        12_000, 0, null, null, 1, earlier, earlier, earlier, null, null, "PENDING",
    )

    private class StubRemote(private val rows: List<CarDto>) : CarRemoteDataSource {
        override suspend fun fetchSince(ownerId: String, since: Instant?): List<CarDto> = rows
        override suspend fun push(cars: List<CarDto>): List<CarDto> = cars
        override suspend fun demoteOtherPrimaries(ownerId: String, keepCarId: String) = Unit
    }
}
