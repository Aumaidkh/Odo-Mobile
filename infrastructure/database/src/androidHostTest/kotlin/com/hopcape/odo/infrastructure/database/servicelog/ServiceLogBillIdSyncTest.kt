package com.hopcape.odo.infrastructure.database.servicelog

import com.hopcape.odo.core.data.servicelog.ServiceLogDto
import com.hopcape.odo.core.data.servicelog.ServiceLogRemoteDataSource
import com.hopcape.odo.core.data.sync.BlobUploader
import com.hopcape.odo.infrastructure.database.sync.inMemoryDatabase
import com.hopcape.odo.infrastructure.database.sync.noopBlobUploaderFileStore
import com.hopcape.odo.infrastructure.database.sync.noopRemoteFileStorage
import com.hopcape.odo.infrastructure.database.sync.silentDataTelemetry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * The bill id must never cross the wire. The server's `service_logs` has
 * `fk_service_logs_bill`, and the client creates no `bills` rows — a pushed bill id is a
 * guaranteed foreign-key refusal (409, permanent) that parks the entry in CONFLICT forever.
 * Found on a device: every scanned entry's sync died on exactly this.
 */
class ServiceLogBillIdSyncTest {

    private fun table(): Pair<ServiceLogSyncTable, com.hopcape.odo.infrastructure.database.db.OdoDatabase> {
        val (database, _) = inMemoryDatabase()
        val syncTable = ServiceLogSyncTable(
            database = database,
            remote = FakeRemote,
            blobs = BlobUploader(noopBlobUploaderFileStore, noopRemoteFileStorage, silentDataTelemetry()),
            carId = { CAR },
        )
        return syncTable to database
    }

    /** Echoes pushes back unchanged; nothing here calls [fetchSince]. */
    private object FakeRemote : ServiceLogRemoteDataSource {
        override suspend fun fetchSince(carId: String, since: Instant?): List<ServiceLogDto> = emptyList()
        override suspend fun push(entries: List<ServiceLogDto>): List<ServiceLogDto> = entries
    }

    private fun com.hopcape.odo.infrastructure.database.db.OdoDatabase.insertScannedLog() =
        serviceLogQueries.insertServiceLog(
            id = LOG,
            carId = CAR,
            ownerId = OWNER,
            serviceDate = "2026-08-04",
            odometerKm = 2400L,
            totalAmountPaise = 425000L,
            workshopName = "Shree Vinayak",
            notes = null,
            source = "SCANNED",
            billId = SCAN,
            billPhotoPath = "scans/local.jpg",
            fairnessSnapshot = null,
            now = "2026-08-04T10:00:00Z",
            syncStatus = "PENDING",
        )

    @Test
    fun `the outbox never carries a bill id`() = runTest {
        val (table, database) = table()
        database.insertScannedLog()

        val pushed = table.pending().single()

        assertNull(pushed.billId)
        // The rest of the row still travels — only the FK trap is withheld.
        assertEquals(LOG, pushed.id)
        assertEquals("scans/local.jpg", pushed.billPhotoPath)
    }

    @Test
    fun `a pulled row without a bill id keeps the local one`() = runTest {
        val (table, database) = table()
        database.insertScannedLog()

        table.applyRemote(
            ServiceLogDto(
                id = LOG,
                carId = CAR,
                ownerId = OWNER,
                serviceDate = "2026-08-04",
                odometerKm = 2400,
                totalAmountPaise = 425000L,
                workshopName = "Shree Vinayak",
                notes = null,
                source = "scanned",
                billId = null,
                billPhotoPath = null,
                fairnessSnapshot = null,
                categories = emptyList(),
                createdAt = "2026-08-04T10:00:00Z",
                updatedAt = "2026-08-04T10:05:00Z",
                deletedAt = null,
            ),
        )

        val row = database.serviceLogQueries.selectById(LOG).executeAsOne()
        assertEquals(SCAN, row.bill_id)
        assertEquals("scans/local.jpg", row.bill_photo_path)
    }

    private companion object {
        const val LOG = "log-1"
        const val CAR = "car-1"
        const val OWNER = "owner-1"
        const val SCAN = "scan-1"
    }
}
