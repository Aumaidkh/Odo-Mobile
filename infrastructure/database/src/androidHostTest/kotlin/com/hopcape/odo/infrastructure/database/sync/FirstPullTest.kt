package com.hopcape.odo.infrastructure.database.sync

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.core.data.servicelog.ServiceLogDto
import com.hopcape.odo.core.data.servicelog.ServiceLogRemoteDataSource
import com.hopcape.odo.core.data.sync.BlobUploader
import com.hopcape.odo.core.sync.SyncCursor
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.infrastructure.database.servicelog.ServiceLogSyncTable
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The first pull after signing into an account that already has data (issue #312).
 *
 * This is the regression test the bug got past. A car-scoped table read the active car from
 * `ActiveCarProvider.activeCarId` — a StateFlow seeded null and filled by a database query —
 * so on the first run after signing in, the engine wrote the pulled cars and reached this
 * table milliseconds later, before that flow had re-emitted. The fetch returned an empty
 * list, which the runner could not tell from a server with nothing new, so the pull reported
 * success, the cursor never moved, and WorkManager dropped the job. The owner was left on an
 * app showing them four first-run empty states.
 *
 * The setup below is that moment made deterministic: a local database with **no cars at all**
 * and a server that has the account's history.
 */
class FirstPullTest {

    private val now = Instant.parse("2026-08-03T10:00:00Z")
    private val owner = "5b28c012-545f-447d-9a85-920084f68246"

    @Test
    fun `service logs arrive even though no car has been written locally yet`() = runTest {
        val (db, driver) = inMemoryDatabase()
        assertEquals(0, driver.count("SELECT COUNT(*) FROM cars"), "the point of the test")
        val remote = FakeRemote(listOf(logDto(id = "log-1", carId = "car-1")))

        val pulled = runner(db, remote).pull(FakeSynchronizer())

        assertTrue(pulled)
        assertEquals(1, driver.count("SELECT COUNT(*) FROM service_logs WHERE id = 'log-1'"))
        assertEquals(owner, remote.askedFor, "the pull is scoped to the account, not to a car")
    }

    @Test
    fun `every car's entries arrive, not just the primary one's`() = runTest {
        // The same change fixes the multi-car gap: a pull keyed on the primary car could
        // never bring down a second car's history.
        val (db, driver) = inMemoryDatabase()
        val remote = FakeRemote(
            listOf(logDto(id = "log-1", carId = "car-1"), logDto(id = "log-2", carId = "car-2")),
        )

        runner(db, remote).pull(FakeSynchronizer())

        assertEquals(2, driver.count("SELECT COUNT(*) FROM service_logs"))
    }

    @Test
    fun `a run with no owner is a retry, not a successful pull of nothing`() = runTest {
        val (db, driver) = inMemoryDatabase()
        val remote = FakeRemote(emptyList())
        val synchronizer = FakeSynchronizer()

        val pulled = runner(db, remote, ownerId = { null }).pull(synchronizer)

        // The whole bug in one assertion: this used to come back true.
        assertTrue(!pulled)
        assertEquals(listOf(SyncEntity.SERVICE_LOGS), synchronizer.failures)
        assertEquals(0, driver.count("SELECT COUNT(*) FROM service_logs"))
        assertEquals(null, remote.askedFor, "nothing may be sent as a filter when there is no owner")
    }

    /* ------------------------------ scaffolding ------------------------------ */

    private fun runner(
        db: com.hopcape.odo.infrastructure.database.db.OdoDatabase,
        remote: FakeRemote,
        ownerId: () -> String? = { owner },
    ) = SyncRunner(
        entity = SyncEntity.SERVICE_LOGS,
        table = ServiceLogSyncTable(
            database = db,
            remote = remote,
            blobs = BlobUploader(noopBlobUploaderFileStore, noopRemoteFileStorage, silentDataTelemetry()),
            ownerId = ownerId,
        ),
        database = db,
        telemetry = silentSyncTelemetry(),
    )

    private fun logDto(id: String, carId: String) = ServiceLogDto(
        id = id,
        carId = carId,
        ownerId = owner,
        serviceDate = "2026-01-15",
        odometerKm = 42_000,
        totalAmountPaise = 280_000,
        source = "manual",
        createdAt = now.toString(),
        updatedAt = now.toString(),
    )

    private class FakeRemote(private val rows: List<ServiceLogDto>) : ServiceLogRemoteDataSource {
        var askedFor: String? = null

        override suspend fun fetchSince(ownerId: String, since: Instant?): List<ServiceLogDto> {
            askedFor = ownerId
            return rows
        }

        override suspend fun push(entries: List<ServiceLogDto>) = entries
    }

    private class FakeSynchronizer : Synchronizer {
        val failures = mutableListOf<SyncEntity>()
        private val cursors = mutableMapOf<SyncEntity, SyncCursor>()

        override suspend fun cursor(entity: SyncEntity): SyncCursor =
            cursors.getOrPut(entity) { SyncCursor(entity) }

        override suspend fun updateCursor(entity: SyncEntity, update: SyncCursor.() -> SyncCursor) {
            cursors[entity] = cursor(entity).update()
        }

        override suspend fun recordFailure(entity: SyncEntity, cause: Throwable) {
            failures += entity
        }
    }

    private fun JdbcSqliteDriver.count(sql: String): Int =
        executeQuery(null, sql, { c -> QueryResult.Value(if (c.next().value) c.getLong(0)!!.toInt() else 0) }, 0).value
}
