package com.hopcape.odo.infrastructure.database.car

import com.hopcape.odo.core.data.car.CarDto
import com.hopcape.odo.core.data.car.CarRemoteDataSource
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.inMemoryDatabase
import com.hopcape.odo.infrastructure.database.sync.silentSyncTelemetry
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

    /* --------------------- the primary flag (SYNC_DESIGN §6.1.3) --------------------- */

    @Test
    fun pushingAPrimaryCarDemotesTheServersOtherPrimariesFirst() = runTest {
        val (db, _) = inMemoryDatabase()
        db.insertLocalCar(id = "local-1")
        // No plate match on the server, so adoption finds nothing — the reinstall case that
        // used to deadlock: the push would be an INSERT against a server that already holds
        // a different primary.
        val remote = RecordingRemote(listOf(serverCar(id = "server-1", plate = "MH01ZZ9999")))
        val table = CarSyncTable(database = db, remote = remote, telemetry = silentSyncTelemetry(), ownerId = { owner })

        table.reconcileBeforePush(table.pending())

        assertEquals(owner to "local-1", remote.demotedFor)
    }

    @Test
    fun aPushWithNoPrimaryLeavesTheServersFlagAlone() = runTest {
        val (db, _) = inMemoryDatabase()
        db.insertLocalCar(id = "local-1", isPrimary = false)
        val remote = RecordingRemote(emptyList())
        val table = CarSyncTable(database = db, remote = remote, telemetry = silentSyncTelemetry(), ownerId = { owner })

        table.reconcileBeforePush(table.pending())

        assertNull(remote.demotedFor)
    }

    @Test
    fun theReclaimKeepsTheAdoptedIdWhenThePlatesMatched() = runTest {
        val (db, _) = inMemoryDatabase()
        db.insertLocalCar(id = "local-1")
        val remote = RecordingRemote(listOf(serverCar(id = "server-1")))
        val table = CarSyncTable(database = db, remote = remote, telemetry = silentSyncTelemetry(), ownerId = { owner })

        table.reconcileBeforePush(table.pending())

        // Adoption re-keyed the row first, so the id spared from demotion is the server's.
        assertEquals(owner to "server-1", remote.demotedFor)
    }

    @Test
    fun aPulledPrimaryCarTakesTheFlagFromTheLocalOne() = runTest {
        val (db, _) = inMemoryDatabase()
        db.insertLocalCar(id = "local-1", remoteVersion = earlier)
        db.carQueries.markSynced(remoteVersion = earlier, id = "local-1")
        val table = table(db, server = emptyList())

        table.applyRemote(serverCar(id = "server-2", plate = "MH01ZZ9999"))

        // Before this, the one-primary index made the INSERT OR IGNORE drop the pulled row
        // silently, and the device never learned which car the account's primary is.
        val rows = db.carQueries.selectById("server-2").executeAsOne()
        assertEquals(1L, rows.is_primary)
        val demoted = db.carQueries.selectById("local-1").executeAsOne()
        assertEquals(0L, demoted.is_primary)
        // The demotion mirrors server state; it is not a local edit to push back.
        assertEquals("SYNCED", demoted.sync_status)
    }

    @Test
    fun aPulledPrimaryTombstoneClaimsNothing() = runTest {
        val (db, _) = inMemoryDatabase()
        db.insertLocalCar(id = "local-1")
        val table = table(db, server = emptyList())

        table.applyRemote(serverCar(id = "server-2", plate = "MH01ZZ9999", deletedAt = now))

        // A deleted car is out of the index's sight on both sides; its flag means nothing.
        assertEquals(1L, db.carQueries.selectById("local-1").executeAsOne().is_primary)
    }

    /* ------------------------------ scaffolding ------------------------------ */

    private fun table(db: OdoDatabase, server: List<CarDto>) =
        CarSyncTable(
            database = db,
            remote = RecordingRemote(server),
            telemetry = silentSyncTelemetry(),
            ownerId = { owner },
        )

    private fun serverCar(
        id: String,
        createdAt: String = earlier,
        deletedAt: String? = null,
        plate: String = this.plate,
    ) = CarDto(
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
        isPrimary: Boolean = true,
    ) = carQueries.insertCar(
        id, owner, "Maruti", "Swift", null, 2019, "PETROL", plate,
        42_000, null, null, if (isPrimary) 1 else 0, now, now, now, null, remoteVersion, "PENDING",
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
        var demotedFor: Pair<String, String>? = null

        override suspend fun fetchSince(ownerId: String, since: Instant?): List<CarDto> {
            fetches++
            return cars
        }

        override suspend fun push(cars: List<CarDto>): List<CarDto> = cars

        override suspend fun demoteOtherPrimaries(ownerId: String, keepCarId: String) {
            demotedFor = ownerId to keepCarId
        }
    }
}
