package com.hopcape.odo.core.data.sync

import app.cash.sqldelight.db.QueryResult
import arrow.core.left
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.file.PlatformFileStore
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.sync.SyncEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The sign-out wipe. Two things here would lose the owner's data if they were wrong, and
 * both are the kind of wrong that looks correct.
 */
class LocalUserDataWipeTest {

    private val now = Instant.parse("2026-08-03T10:00:00Z")
    private val owner = "5b28c012-545f-447d-9a85-920084f68246"

    @Test
    fun everyOwnedRowIsGone() = runTest {
        val (db, driver) = seeded()

        wipe(db).wipe()

        listOf("profiles", "cars", "service_logs", "documents", "health_scores", "overcharge_reports")
            .forEach { table -> assertEquals(0, driver.count("SELECT COUNT(*) FROM $table"), table) }
        assertEquals(0, driver.count("SELECT COUNT(*) FROM service_log_categories"))
    }

    @Test
    fun theDeletesAreHard_soSigningOutCannotDestroyTheBackup() = runTest {
        val (db, driver) = seeded()

        wipe(db).wipe()

        // A soft delete would leave the row with deleted_at set and PENDING, and the next
        // sign-in would push a tombstone for it — signing out would delete the owner's
        // cloud copy. Nothing may survive as a tombstone.
        assertEquals(0, driver.count("SELECT COUNT(*) FROM cars WHERE deleted_at IS NOT NULL"))
        assertEquals(0, driver.count("SELECT COUNT(*) FROM cars WHERE sync_status = 'PENDING'"))
        assertEquals(0, driver.count("SELECT COUNT(*) FROM service_logs"))
    }

    @Test
    fun theSyncCursorsAreCleared() = runTest {
        val (db, driver) = seeded()
        db.syncStateQueries.transaction {
            db.syncStateQueries.insertIgnore(SyncEntity.CARS.name)
            db.syncStateQueries.update(
                lastPulledAt = now.toString(),
                lastPushedAt = now.toString(),
                lastError = null,
                entity = SyncEntity.CARS.name,
            )
        }

        wipe(db).wipe()

        // A cursor left behind would make the next sign-in's pull start from it and skip
        // everything written in between — rows that exist on the server and never arrive.
        assertEquals(0, driver.count("SELECT COUNT(*) FROM sync_state"))
    }

    @Test
    fun storedFilesGoWithTheRowsThatNamedThem() = runTest {
        val (db, _) = seeded()
        val files = RecordingFileStore()

        wipe(db, files).wipe()

        // Bill photos and insurance papers. A blob whose row is gone is unreachable bytes.
        assertTrue(files.deleted.contains("documents/doc-1.pdf"), files.deleted.toString())
        assertTrue(files.deleted.contains("bills/log-1.jpg"), files.deleted.toString())
    }

    @Test
    fun devicePreferencesSurvive() = runTest {
        val (db, driver) = seeded()
        driver.exec(
            "INSERT INTO app_settings (id, theme, larger_text, distance_unit, fuel_efficiency_unit, " +
                "notif_doc_expiry, notif_service_due, notif_custom, notif_overcharge, notif_monthly, " +
                "notif_health_drop, notif_push, updated_at) " +
                "VALUES (1, 'DARK', 1, 'KM', 'KMPL', 1, 1, 1, 1, 1, 1, 1, '$now')",
        )

        wipe(db).wipe()

        // Theme, text size and units describe the phone, not the account. Someone signing
        // back in has not asked for their text size to change.
        assertEquals(1, driver.count("SELECT COUNT(*) FROM app_settings"))
        assertEquals("DARK", driver.text("SELECT theme FROM app_settings WHERE id = 1"))
    }

    @Test
    fun wipingAnEmptyDatabaseIsFine() = runTest {
        val (db, driver) = inMemoryDatabase()

        // Sign-out before anything was ever written, or a second sign-out.
        wipe(db).wipe()

        assertEquals(0, driver.count("SELECT COUNT(*) FROM cars"))
    }

    /* ------------------------------ scaffolding ------------------------------ */

    private fun wipe(db: OdoDatabase, files: RecordingFileStore = RecordingFileStore()) =
        SqlDelightLocalUserDataWipe(db, files, silentDataTelemetry())

    private fun seeded(): Pair<OdoDatabase, JdbcSqliteDriver> {
        val (db, driver) = inMemoryDatabase()
        driver.exec("INSERT INTO profiles (id, full_name, created_at, updated_at, sync_status) VALUES ('$owner', 'Rahul', '$now', '$now', 'SYNCED')")
        driver.exec("INSERT INTO cars (id, owner_id, make, model, year, fuel_type, current_odometer_km, created_at, updated_at, sync_status) VALUES ('car-1', '$owner', 'Maruti', 'Swift', 2019, 'PETROL', 42000, '$now', '$now', 'SYNCED')")
        driver.exec("INSERT INTO service_logs (id, car_id, owner_id, service_date, odometer_km, total_amount_paise, source, bill_photo_path, created_at, updated_at, sync_status) VALUES ('log-1', 'car-1', '$owner', '2026-01-15', 42000, 280000, 'MANUAL', 'bills/log-1.jpg', '$now', '$now', 'SYNCED')")
        driver.exec("INSERT INTO service_log_categories (service_log_id, category) VALUES ('log-1', 'OIL_CHANGE')")
        driver.exec("INSERT INTO documents (id, car_id, owner_id, doc_type, storage_path, doc_source, created_at, updated_at, sync_status) VALUES ('doc-1', 'car-1', '$owner', 'INSURANCE', 'documents/doc-1.pdf', 'UPLOADED', '$now', '$now', 'SYNCED')")
        driver.exec("INSERT INTO health_scores (id, car_id, owner_id, score, maintenance_pts, documentation_pts, cost_efficiency_pts, history_pts, algo_version, computed_at, created_at, updated_at, sync_status) VALUES ('hs-1', 'car-1', '$owner', 72, 25, 20, 15, 12, 'rule-v1', '$now', '$now', '$now', 'SYNCED')")
        driver.exec("INSERT INTO overcharge_reports (id, service_log_id, owner_id, reason, created_at, updated_at, sync_status) VALUES ('oc-1', 'log-1', '$owner', 'ABOVE_MARKET_RATE', '$now', '$now', 'SYNCED')")
        return db to driver
    }

    private class RecordingFileStore : PlatformFileStore {
        val deleted = mutableListOf<String>()
        override suspend fun save(pickedRef: String, directory: String, fileName: String) =
            DomainError.PersistenceFailure("unused").left()

        override suspend fun delete(storageKey: String) { deleted += storageKey }
        override suspend fun exists(storageKey: String) = true
        override suspend fun bytes(storageKey: String) = DomainError.PersistenceFailure("unused").left()
    }

    private fun JdbcSqliteDriver.exec(sql: String) = execute(null, sql, 0)

    private fun JdbcSqliteDriver.count(sql: String): Int =
        executeQuery(null, sql, { c -> QueryResult.Value(if (c.next().value) c.getLong(0)!!.toInt() else 0) }, 0).value

    private fun JdbcSqliteDriver.text(sql: String): String? =
        executeQuery(null, sql, { c -> QueryResult.Value(if (c.next().value) c.getString(0) else null) }, 0).value
}
