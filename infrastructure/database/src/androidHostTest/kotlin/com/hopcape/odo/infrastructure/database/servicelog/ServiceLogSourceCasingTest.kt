package com.hopcape.odo.infrastructure.database.servicelog

import com.hopcape.odo.core.data.servicelog.ServiceLogDto
import com.hopcape.odo.core.data.servicelog.ServiceLogRemoteDataSource
import com.hopcape.odo.core.data.sync.BlobUploader
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.inMemoryDatabase
import com.hopcape.odo.infrastructure.database.sync.noopBlobUploaderFileStore
import com.hopcape.odo.infrastructure.database.sync.noopRemoteFileStorage
import com.hopcape.odo.infrastructure.database.sync.silentDataTelemetry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * `source` is spelled two ways on purpose, and the boundary is the only place that knows.
 *
 * The local column holds Kotlin constant names (`DECLARED`); the server column is a Postgres
 * enum whose labels are lowercase (`declared`). A row pushed in the wrong case is refused
 * outright — `invalid input value for enum log_source` — and the owner sees nothing, because
 * a failed push is silent and the row simply stays PENDING forever.
 *
 * This became worth pinning when `DECLARED` was added: the migration adding the label was
 * first written in the local casing, which would have added a value the client never sends
 * while leaving every push of a remembered service refused.
 */
class ServiceLogSourceCasingTest {

    @Test
    fun `a declared service travels in the server's casing`() = runTest {
        val (table, database) = table()
        database.insertLog(source = "DECLARED")

        assertEquals("declared", table.pending().single().source)
    }

    @Test
    fun `the other sources travel the same way`() = runTest {
        val (table, database) = table()
        database.insertLog(id = "log-manual", source = "MANUAL")
        database.insertLog(id = "log-scanned", source = "SCANNED")

        assertEquals(
            listOf("manual", "scanned"),
            table.pending().map { it.source }.sorted(),
        )
    }

    /** And back the other way, so a row pulled from the server is readable locally. */
    @Test
    fun `a declared row pulled from the server is stored in the local casing`() = runTest {
        val (table, database) = table()

        table.applyRemote(dto(source = "declared"))

        assertEquals("DECLARED", database.serviceLogQueries.selectById(LOG).executeAsOne().source)
    }

    /* ------------------------------ Fixtures ------------------------------ */

    private fun table(): Pair<ServiceLogSyncTable, OdoDatabase> {
        val (database, _) = inMemoryDatabase()
        val syncTable = ServiceLogSyncTable(
            database = database,
            remote = FakeRemote,
            blobs = BlobUploader(noopBlobUploaderFileStore, noopRemoteFileStorage, silentDataTelemetry()),
            ownerId = { OWNER },
        )
        return syncTable to database
    }

    private object FakeRemote : ServiceLogRemoteDataSource {
        override suspend fun fetchSince(ownerId: String, since: Instant?): List<ServiceLogDto> = emptyList()
        override suspend fun push(entries: List<ServiceLogDto>): List<ServiceLogDto> = entries
    }

    private fun OdoDatabase.insertLog(id: String = LOG, source: String) =
        serviceLogQueries.insertServiceLog(
            id = id,
            carId = CAR,
            ownerId = OWNER,
            serviceDate = "2026-08-04",
            odometerKm = 2400L,
            // A declared service carries no money, which is the shape this row really has.
            totalAmountPaise = 0L,
            workshopName = null,
            notes = null,
            source = source,
            billId = null,
            billPhotoPath = null,
            fairnessSnapshot = null,
            lineItems = null,
            now = "2026-08-04T10:00:00Z",
            syncStatus = "PENDING",
        )

    private fun dto(source: String) = ServiceLogDto(
        id = LOG,
        carId = CAR,
        ownerId = OWNER,
        serviceDate = "2026-08-04",
        odometerKm = 2400,
        totalAmountPaise = 0L,
        workshopName = null,
        notes = null,
        source = source,
        billId = null,
        billPhotoPath = null,
        fairnessSnapshot = null,
        categories = emptyList(),
        createdAt = "2026-08-04T10:00:00Z",
        updatedAt = "2026-08-04T10:05:00Z",
        deletedAt = null,
    )

    private companion object {
        const val OWNER = "owner-1"
        const val CAR = "car-1"
        const val LOG = "log-1"
    }
}
