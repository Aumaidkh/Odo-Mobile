package com.hopcape.odo.infrastructure.database.trip

import com.hopcape.odo.core.data.trip.TripDto
import com.hopcape.odo.core.data.trip.TripRemoteDataSource
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import com.hopcape.odo.infrastructure.database.sync.FetchResult
import com.hopcape.odo.infrastructure.database.sync.inMemoryDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * `trips` as [TripSyncTable] sees it (TRIPTRACKER_PLAN D3/D4).
 *
 * Mirrors `ServiceLogBillIdSyncTest`'s shape: a hand-written [TripRemoteDataSource] fake
 * and the in-memory driver, exercising the [com.hopcape.odo.infrastructure.database.sync.SyncTable]
 * contract directly rather than through [com.hopcape.odo.infrastructure.database.sync.SyncRunner]
 * — the runner's push/pull/conflict algorithm is already covered generically in
 * `SyncRunnerTest` and does not need a per-entity copy.
 */
class TripSyncTableTest {

    @Test
    fun `a pending trip is pushed with no coordinate fields to carry`() = runTest {
        val (database, _) = inMemoryDatabase()
        database.insertLocalTrip(id = TRIP, startLat = 19.07, startLon = 72.87, endLat = 19.08, endLon = 72.88)
        val table = table(database)

        val pushed = table.pending()

        assertEquals(listOf(TRIP), pushed.map { it.id })
        assertEquals("bt_verified", pushed.single().mode)
        assertEquals("recorded", pushed.single().status)
    }

    @Test
    fun `pushing delegates to the remote and marks the row synced`() = runTest {
        val (database, _) = inMemoryDatabase()
        database.insertLocalTrip(id = TRIP)
        val remote = RecordingRemote()
        val table = table(database, remote)

        val stored = table.push(table.pending())

        assertEquals(listOf(TRIP), remote.pushed.map { it.id })
        assertEquals(listOf(TRIP), stored.map { it.id })
    }

    @Test
    fun `a new remote trip is inserted with no coordinates`() = runTest {
        val (database, _) = inMemoryDatabase()
        val table = table(database)

        table.applyRemote(remoteTrip(id = TRIP, updatedAt = T1))

        val row = database.tripQueries.selectById(TRIP, ::tripFromRow).executeAsOne()
        assertEquals(TRIP, row.id.value)
        assertNull(row.startPoint)
        assertNull(row.endPoint)
        val state = table.localState(TRIP)
        assertEquals(SyncStatus.SYNCED, state?.syncStatus)
    }

    @Test
    fun `applyRemote never blanks a coordinate value already on the local row`() = runTest {
        val (database, _) = inMemoryDatabase()
        // A trip this device finalized itself, with real coordinates — the four columns
        // the server never carries.
        database.insertLocalTrip(id = TRIP, startLat = 19.07, startLon = 72.87, endLat = 19.08, endLon = 72.88)
        database.tripQueries.markSynced(remoteVersion = T0.toString(), id = TRIP)
        val table = table(database)

        // A pull applying the same row back — the DTO structurally has no coordinates to
        // send, so a naive full-row replace would blank them (D4's exact failure mode).
        table.applyRemote(remoteTrip(id = TRIP, updatedAt = T1))

        val row = database.tripQueries.selectById(TRIP, ::tripFromRow).executeAsOne()
        assertEquals(19.07, row.startPoint?.lat)
        assertEquals(72.87, row.startPoint?.lon)
        assertEquals(19.08, row.endPoint?.lat)
        assertEquals(72.88, row.endPoint?.lon)
    }

    @Test
    fun `applyRemote updates the mutable fields of an existing row`() = runTest {
        val (database, _) = inMemoryDatabase()
        database.insertLocalTrip(id = TRIP, status = "NEEDS_CONFIRMATION")
        database.tripQueries.markSynced(remoteVersion = T0.toString(), id = TRIP)

        table(database).applyRemote(remoteTrip(id = TRIP, status = "confirmed", updatedAt = T1))

        val row = database.tripQueries.selectSyncState(TRIP).executeAsOne()
        assertEquals("SYNCED", row.sync_status)
        assertEquals(T1.toString(), row.updated_at)
    }

    @Test
    fun `fetch reports a missing scope when there is no owner`() = runTest {
        val (database, _) = inMemoryDatabase()
        val remote = RecordingRemote()
        val table = TripSyncTable(database = database, remote = remote, ownerId = { null })

        // Deliberately not an empty page: an empty page means the server had nothing, and a
        // run that cannot ask has established no such thing (issue #312).
        assertIs<FetchResult.ScopeMissing>(table.fetch(since = null))
        assertEquals(0, remote.fetchCalls)
    }

    @Test
    fun `fetch asks the remote for every trip on the account`() = runTest {
        val (database, _) = inMemoryDatabase()
        val remote = RecordingRemote(remoteResult = listOf(remoteTrip(id = TRIP, updatedAt = T1)))
        val table = TripSyncTable(database = database, remote = remote, ownerId = { OWNER })

        val fetched = table.fetch(since = T0)

        // Owner-scoped, so a second car's trips arrive too, and nothing depends on the
        // active-car flow having emitted yet.
        assertIs<FetchResult.Rows<TripDto>>(fetched)
        assertEquals(listOf(TRIP), fetched.rows.map { it.id })
        assertEquals(OWNER, remote.lastOwnerId)
        assertEquals(T0, remote.lastSince)
    }

    /* ------------------------------ scaffolding ------------------------------ */

    private fun table(
        database: OdoDatabase,
        remote: TripRemoteDataSource = RecordingRemote(),
    ) = TripSyncTable(database = database, remote = remote, ownerId = { OWNER })

    private fun OdoDatabase.insertLocalTrip(
        id: String,
        status: String = "RECORDED",
        startLat: Double? = null,
        startLon: Double? = null,
        endLat: Double? = null,
        endLon: Double? = null,
    ) = tripQueries.insertTrip(
        id = id,
        carId = CAR,
        ownerId = OWNER,
        startedAt = T0.toString(),
        endedAt = T1.toString(),
        distanceM = 5_000L,
        estimatedM = 0L,
        mode = "BT_VERIFIED",
        status = status,
        startLat = startLat,
        startLon = startLon,
        endLat = endLat,
        endLon = endLon,
        now = T0.toString(),
        syncStatus = "PENDING",
    )

    private fun remoteTrip(id: String, status: String = "recorded", updatedAt: Instant) = TripDto(
        id = id,
        carId = CAR,
        ownerId = OWNER,
        startedAt = T0.toString(),
        endedAt = T1.toString(),
        distanceM = 5_000L,
        estimatedM = 0L,
        mode = "bt_verified",
        status = status,
        createdAt = T0.toString(),
        updatedAt = updatedAt.toString(),
        deletedAt = null,
    )

    /** Echoes pushes back unchanged, and answers [fetchSince] with a fixed page. */
    private class RecordingRemote(private val remoteResult: List<TripDto> = emptyList()) : TripRemoteDataSource {
        val pushed = mutableListOf<TripDto>()
        var fetchCalls = 0
        var lastOwnerId: String? = null
        var lastSince: Instant? = null

        override suspend fun fetchSince(ownerId: String, since: Instant?): List<TripDto> {
            fetchCalls++
            lastOwnerId = ownerId
            lastSince = since
            return remoteResult
        }

        override suspend fun push(trips: List<TripDto>): List<TripDto> {
            pushed += trips
            return trips
        }
    }

    private companion object {
        const val TRIP = "trip-1"
        const val CAR = "car-1"
        const val OWNER = "owner-1"
        val T0 = Instant.parse("2026-08-01T10:00:00Z")
        val T1 = Instant.parse("2026-08-01T11:00:00Z")
    }
}
