package com.hopcape.odo.core.data.car

import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.sync.inMemoryDatabase
import com.hopcape.odo.core.data.sync.silentSyncTelemetry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Re-adding a car the server already holds (SYNC_DESIGN §7).
 *
 * The server keeps one live car per plate per owner. A reinstall wipes the local database,
 * so the same car onboarded again carries a new id for a plate the server still has — and
 * the push, an upsert on the primary key, would be an INSERT that breaks that rule. A
 * duplicate key is refused the same way forever, so without this the car and every service
 * log hanging off it would never sync again.
 */
class CarSyncTableTest {

    private val owner = "owner-1"
    private val plate = "MH01AB1234"
    private val now = Instant.parse("2026-08-01T10:00:00Z").toString()
    private val earlier = Instant.parse("2026-01-01T10:00:00Z").toString()

    @Test
    fun aLocalCarTakesOnTheServersIdWhenTheirPlatesMatch() = runTest {
        val (db, _) = inMemoryDatabase()
        db.insertLocalCar(id = "local-1")
        val table = table(db, server = listOf(serverCar(id = "server-1")))

        val rows = table.reconcileBeforePush(table.pending())

        // The push that follows now updates the row the server already has.
        assertEquals(listOf("server-1"), rows.map { it.id })
        assertEquals("server-1", db.carQueries.selectPrimaryCar().executeAsOne().id)
        assertNull(db.carQueries.selectById("local-1").executeAsOneOrNull())
    }

    @Test
    fun theServersCreatedAtComesAcrossWithItsId() = runTest {
        val (db, _) = inMemoryDatabase()
        db.insertLocalCar(id = "local-1")
        val table = table(db, server = listOf(serverCar(id = "server-1", createdAt = earlier)))

        val rows = table.reconcileBeforePush(table.pending())

        // The local row thinks the car was added today; the server has had it since the
        // first install, and that is the true date.
        assertEquals(earlier, rows.single().createdAt)
        assertEquals(earlier, db.carQueries.selectById("server-1").executeAsOne().created_at)
    }

    @Test
    fun theCarsServiceLogsFollowItToTheNewId() = runTest {
        val (db, _) = inMemoryDatabase()
        db.insertLocalCar(id = "local-1")
        db.insertServiceLog(id = "log-1", carId = "local-1")
        val table = table(db, server = listOf(serverCar(id = "server-1")))

        table.reconcileBeforePush(table.pending())

        // A car that moved while its history did not would leave the owner's logs pointing
        // at a row that no longer exists.
        assertEquals("server-1", db.serviceLogQueries.selectPending().executeAsOne().car_id)
    }

    @Test
    fun aSoftDeletedServerCarDoesNotClaimThePlate() = runTest {
        val (db, _) = inMemoryDatabase()
        db.insertLocalCar(id = "local-1")
        val table = table(db, server = listOf(serverCar(id = "server-1", deletedAt = now)))

        val rows = table.reconcileBeforePush(table.pending())

        // Removing a car frees its plate, so re-adding it really is a new row.
        assertEquals(listOf("local-1"), rows.map { it.id })
    }

    @Test
    fun aCarThatHasSyncedBeforeIsNotLookedUp() = runTest {
        val (db, _) = inMemoryDatabase()
        db.insertLocalCar(id = "local-1", remoteVersion = earlier)
        val remote = RecordingRemote(listOf(serverCar(id = "server-1")))
        val table = CarSyncTable(database = db, remote = remote, telemetry = silentSyncTelemetry(), ownerId = { owner })

        val rows = table.reconcileBeforePush(table.pending())

        // Its id is already the server's, so there is nothing to reconcile and no reason to
        // spend a request finding that out.
        assertEquals(listOf("local-1"), rows.map { it.id })
        assertTrue(remote.fetches == 0)
    }

    @Test
    fun aCarWithNoPlateIsLeftAlone() = runTest {
        val (db, _) = inMemoryDatabase()
        db.insertLocalCar(id = "local-1", plate = null)
        val remote = RecordingRemote(listOf(serverCar(id = "server-1")))
        val table = CarSyncTable(database = db, remote = remote, telemetry = silentSyncTelemetry(), ownerId = { owner })

        val rows = table.reconcileBeforePush(table.pending())

        // Nothing to match on: the plate is what the server's rule is about.
        assertEquals(listOf("local-1"), rows.map { it.id })
        assertTrue(remote.fetches == 0)
    }

    /* ------------------------------ scaffolding ------------------------------ */

    private fun table(db: OdoDatabase, server: List<CarDto>) =
        CarSyncTable(
            database = db,
            remote = RecordingRemote(server),
            telemetry = silentSyncTelemetry(),
            ownerId = { owner },
        )

    private fun serverCar(id: String, createdAt: String = earlier, deletedAt: String? = null) = CarDto(
        id = id,
        ownerId = owner,
        make = "Maruti",
        model = "Swift",
        year = 2019,
        fuelType = "petrol",
        registrationNumber = plate,
        currentOdometerKm = 41_000,
        isPrimary = true,
        createdAt = createdAt,
        updatedAt = createdAt,
        deletedAt = deletedAt,
    )

    private fun OdoDatabase.insertLocalCar(
        id: String,
        plate: String? = this@CarSyncTableTest.plate,
        remoteVersion: String? = null,
    ) = carQueries.insertCar(
        id, owner, "Maruti", "Swift", null, 2019, "PETROL", plate,
        42_000, null, null, 1, now, now, now, null, remoteVersion, "PENDING",
    )

    private fun OdoDatabase.insertServiceLog(id: String, carId: String) =
        serviceLogQueries.insertServiceLog(
            id = id,
            carId = carId,
            ownerId = owner,
            serviceDate = "2026-07-01",
            odometerKm = 42_000,
            totalAmountPaise = 280_000,
            workshopName = null,
            notes = null,
            source = "MANUAL",
            billId = null,
            billPhotoPath = null,
            fairnessSnapshot = null,
            now = now,
            syncStatus = "PENDING",
        )

    /** Answers with a fixed server table, and counts how often it was asked. */
    private class RecordingRemote(private val cars: List<CarDto>) : CarRemoteDataSource {
        var fetches = 0
        override suspend fun fetchSince(ownerId: String, since: Instant?): List<CarDto> {
            fetches++
            return cars
        }

        override suspend fun push(cars: List<CarDto>): List<CarDto> = cars
    }
}
