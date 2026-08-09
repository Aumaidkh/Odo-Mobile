package com.hopcape.odo.infrastructure.database.fairness

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import com.hopcape.odo.core.domain.fairness.model.OverchargeReason
import com.hopcape.odo.core.domain.fairness.model.OverchargeReport
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * SQL behaviour for [SqlDelightOverchargeReportLocalDataSource] — the cross-table read
 * that derives `owner_id` from the reported service log, in the same transaction as the
 * insert. Error mapping, id generation, and sync scheduling live in
 * [OverchargeReportRepositoryImplTest] instead, against a fake port.
 */
class SqlDelightOverchargeReportLocalDataSourceTest {

    private fun newDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private fun OdoDatabase.seedLog(id: String = "log-1", ownerId: String = "owner-1") {
        serviceLogQueries.insertServiceLog(
            id = id, carId = "car-1", ownerId = ownerId, serviceDate = "2026-06-15",
            odometerKm = 50_000, totalAmountPaise = 330_000, workshopName = null, notes = null,
            source = "MANUAL", billId = null, billPhotoPath = null, fairnessSnapshot = null,
            lineItems = null,
            now = "2026-07-30T10:00:00Z", syncStatus = SyncStatus.PENDING.name,
        )
    }

    private fun local(db: OdoDatabase) = SqlDelightOverchargeReportLocalDataSource(
        database = db,
        clock = object : Clock { override fun now() = Instant.parse("2026-07-30T11:00:00Z") },
        dispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun insert_storesTheReportPendingWithTheOwnerTakenFromTheEntry() = runTest {
        val db = newDb().apply { seedLog(ownerId = "owner-7") }

        val inserted = local(db).insert(
            "report-1",
            OverchargeReport(
                logId = ServiceLogId("log-1"),
                reason = OverchargeReason.ABOVE_MARKET_RATE,
                category = ServiceCategory.BRAKES,
                note = "quoted 2x",
            ),
        )

        assertTrue(inserted)
        val stored = db.overchargeReportQueries.selectByServiceLog("log-1").executeAsOne()
        assertEquals("report-1", stored.id)
        assertEquals(OverchargeReason.ABOVE_MARKET_RATE.name, stored.reason)
        assertEquals(ServiceCategory.BRAKES.name, stored.category)
        assertEquals("quoted 2x", stored.note)
        // Ownership is derived from the entry, never asserted by the client.
        assertEquals("owner-7", stored.owner_id)
        // Nothing can push it yet, so it waits.
        assertEquals(SyncStatus.PENDING.name, stored.sync_status)
    }

    @Test
    fun insert_unknownEntry_answersFalseAndWritesNothing() = runTest {
        val db = newDb()

        val inserted = local(db).insert(
            "report-1",
            OverchargeReport(logId = ServiceLogId("ghost"), reason = OverchargeReason.WORK_NOT_DONE),
        )

        assertFalse(inserted)
        assertTrue(db.overchargeReportQueries.selectByServiceLog("ghost").executeAsList().isEmpty())
    }
}
