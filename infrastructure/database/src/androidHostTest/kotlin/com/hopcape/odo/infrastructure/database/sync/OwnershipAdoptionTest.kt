package com.hopcape.odo.infrastructure.database.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * Sign-in adoption (SYNC_DESIGN §9): everything created before there was an account moves
 * onto the account that just signed in.
 *
 * The property that matters most is that running it twice is the same as running it once.
 * It runs before *every* sync pass rather than behind a "have we adopted" flag, because a
 * flag can be wrong and this query cannot.
 */
class OwnershipAdoptionTest {

    private val now = Instant.parse("2026-08-03T10:00:00Z")
    private val placeholder = "local-owner"
    private val real = "5b28c012-545f-447d-9a85-920084f68246"

    /** An account that signed out on this device without the wipe completing. */
    private val other = "9f11a0e4-2c77-4b19-a0d0-1d9c2f3b7e55"

    @Test
    fun everyOwnedRowMovesToTheRealAccountAndGoesPending() = runTest {
        val (db, driver) = seeded()

        adoption(db).adopt(real, now)

        listOf("cars", "service_logs", "documents", "health_scores", "overcharge_reports", "vehicle_catalog_submissions").forEach { table ->
            assertEquals(0, driver.count("SELECT COUNT(*) FROM $table WHERE owner_id = '$placeholder'"), table)
            assertEquals(1, driver.count("SELECT COUNT(*) FROM $table WHERE owner_id = '$real'"), table)
            assertEquals(
                1,
                driver.count("SELECT COUNT(*) FROM $table WHERE sync_status = 'PENDING'"),
                "$table must go back in the outbox — the server has never seen these rows",
            )
        }
    }

    @Test
    fun theProfileIsReKeyed_becauseItsPrimaryKeyIsTheOwner() = runTest {
        val (db, driver) = seeded()

        adoption(db).adopt(real, now)

        assertEquals(0, driver.count("SELECT COUNT(*) FROM profiles WHERE id = '$placeholder'"))
        assertEquals(1, driver.count("SELECT COUNT(*) FROM profiles WHERE id = '$real'"))
    }

    @Test
    fun runningItTwiceChangesNothingTheSecondTime() = runTest {
        val (db, driver) = seeded()

        adoption(db).adopt(real, now)
        val after = driver.snapshot()
        adoption(db).adopt(real, now.plus(kotlin.time.Duration.parse("1h")))

        assertEquals(after, driver.snapshot(), "adoption must be idempotent — it runs before every pass")
    }

    @Test
    fun rowsAlreadyOwnedByTheAccountAreLeftAlone() = runTest {
        val (db, driver) = seeded()
        driver.exec(
            "INSERT INTO cars (id, owner_id, make, model, year, fuel_type, current_odometer_km, " +
                "created_at, updated_at, sync_status) VALUES ('car-synced', '$real', 'Honda', 'City', " +
                "2020, 'PETROL', 5000, '$now', '$now', 'SYNCED')",
        )

        adoption(db).adopt(real, now)

        // A row that already belongs to the account is not the placeholder's, so it keeps
        // its SYNCED status instead of being pushed again for no reason.
        assertEquals(1, driver.count("SELECT COUNT(*) FROM cars WHERE id = 'car-synced' AND sync_status = 'SYNCED'"))
    }

    @Test
    fun aProfileAlreadyPulledForTheAccountSurvives_andThePlaceholderIsDropped() = runTest {
        val (db, driver) = seeded()
        driver.exec(
            "INSERT INTO profiles (id, full_name, created_at, updated_at, sync_status) " +
                "VALUES ('$real', 'From Server', '$now', '$now', 'SYNCED')",
        )

        adoption(db).adopt(real, now)

        // The re-key loses to the existing row (UPDATE OR IGNORE), and the placeholder is
        // deleted rather than left behind as a second, ownerless profile.
        assertEquals("From Server", driver.text("SELECT full_name FROM profiles WHERE id = '$real'"))
        assertEquals(0, driver.count("SELECT COUNT(*) FROM profiles WHERE id = '$placeholder'"))
    }

    @Test
    fun adoptingToThePlaceholderItselfIsANoOp() = runTest {
        val (db, driver) = seeded()
        val before = driver.snapshot()

        // Guards the case where sign-in somehow reports the placeholder as the real id —
        // without the check, every row would be marked PENDING on every single pass.
        adoption(db).adopt(placeholder, now)

        assertEquals(before, driver.snapshot())
    }

    /* ---------------- finishing a sign-out that did not finish ---------------- */

    @Test
    fun rowsBelongingToAPreviousAccountAreEvicted() = runTest {
        // `LocalUserDataWipe` swallows a failed transaction, so a device can still be
        // carrying the last account's rows. They cannot be pushed (RLS refuses an owner_id
        // that is not auth.uid()), and they show on screen as if they belonged to whoever is
        // signed in now.
        val (db, driver) = seeded()
        driver.seedOtherAccount()

        adoption(db).adopt(real, now)

        listOf("cars", "service_logs", "documents", "health_scores", "overcharge_reports", "vehicle_catalog_submissions", "trips")
            .forEach { table ->
                assertEquals(0, driver.count("SELECT COUNT(*) FROM $table WHERE owner_id = '$other'"), table)
            }
        assertEquals(0, driver.count("SELECT COUNT(*) FROM profiles WHERE id = '$other'"))
    }

    @Test
    fun theOwnersOwnRowsSurviveTheEviction() = runTest {
        // The eviction runs *after* adoption, so work done offline has already become this
        // account's and is never what gets removed.
        val (db, driver) = seeded()
        driver.seedOtherAccount()

        adoption(db).adopt(real, now)

        assertEquals(1, driver.count("SELECT COUNT(*) FROM cars WHERE owner_id = '$real'"))
        assertEquals(1, driver.count("SELECT COUNT(*) FROM service_logs WHERE owner_id = '$real'"))
        assertEquals(1, driver.count("SELECT COUNT(*) FROM profiles WHERE id = '$real'"))
    }

    @Test
    fun evictingAPreviousAccountAlsoClearsTheSyncCursors() = runTest {
        // The cursors described the evicted account's pull. Left in place, this account's
        // first pull is a delta since a mark it never set — which for an established account
        // fetches nothing at all (issue #312).
        val (db, driver) = seeded()
        driver.seedOtherAccount()
        db.syncStateQueries.transaction {
            db.syncStateQueries.insertIgnore(SyncEntity.CARS.name)
            db.syncStateQueries.update(
                lastPulledAt = now.toString(),
                lastPushedAt = now.toString(),
                lastError = null,
                entity = SyncEntity.CARS.name,
            )
        }

        adoption(db).adopt(real, now)

        assertEquals(0, driver.count("SELECT COUNT(*) FROM sync_state"))
    }

    @Test
    fun aCleanDeviceKeepsItsCursors() = runTest {
        // The eviction has to be self-limiting. Clearing the cursors on every pass would
        // make every run a full re-pull.
        val (db, driver) = seeded()
        adoption(db).adopt(real, now)
        db.syncStateQueries.transaction {
            db.syncStateQueries.insertIgnore(SyncEntity.CARS.name)
            db.syncStateQueries.update(
                lastPulledAt = now.toString(),
                lastPushedAt = now.toString(),
                lastError = null,
                entity = SyncEntity.CARS.name,
            )
        }

        adoption(db).adopt(real, now)

        assertEquals(1, driver.count("SELECT COUNT(*) FROM sync_state"))
    }

    @Test
    fun categoriesGoWithTheEntriesTheyBelongedTo() = runTest {
        // They carry no owner of their own, so they are cleaned up by parentage.
        val (db, driver) = seeded()
        driver.seedOtherAccount()
        driver.exec("INSERT INTO service_log_categories (service_log_id, category) VALUES ('other-log', 'OIL_CHANGE')")

        adoption(db).adopt(real, now)

        assertEquals(0, driver.count("SELECT COUNT(*) FROM service_log_categories WHERE service_log_id = 'other-log'"))
    }

    /* ------------------------------ scaffolding ------------------------------ */

    private fun adoption(db: OdoDatabase) =
        SqlDelightOwnershipAdoption(db, silentDataTelemetry(), placeholder)

    /** One placeholder-owned row in every user-owned table. */
    private fun seeded(): Pair<OdoDatabase, JdbcSqliteDriver> {
        val (db, driver) = inMemoryDatabase()
        driver.exec("INSERT INTO profiles (id, full_name, created_at, updated_at, sync_status) VALUES ('$placeholder', 'Rahul', '$now', '$now', 'SYNCED')")
        driver.exec("INSERT INTO cars (id, owner_id, make, model, year, fuel_type, current_odometer_km, created_at, updated_at, sync_status) VALUES ('car-1', '$placeholder', 'Maruti', 'Swift', 2019, 'PETROL', 42000, '$now', '$now', 'SYNCED')")
        driver.exec("INSERT INTO service_logs (id, car_id, owner_id, service_date, odometer_km, total_amount_paise, source, created_at, updated_at, sync_status) VALUES ('log-1', 'car-1', '$placeholder', '2026-01-15', 42000, 280000, 'MANUAL', '$now', '$now', 'SYNCED')")
        driver.exec("INSERT INTO documents (id, car_id, owner_id, doc_type, storage_path, doc_source, created_at, updated_at, sync_status) VALUES ('doc-1', 'car-1', '$placeholder', 'INSURANCE', 'docs/doc-1.pdf', 'UPLOADED', '$now', '$now', 'SYNCED')")
        driver.exec("INSERT INTO health_scores (id, car_id, owner_id, score, maintenance_pts, documentation_pts, cost_efficiency_pts, history_pts, algo_version, computed_at, created_at, updated_at, sync_status) VALUES ('hs-1', 'car-1', '$placeholder', 72, 25, 20, 15, 12, 'rule-v1', '$now', '$now', '$now', 'SYNCED')")
        driver.exec("INSERT INTO overcharge_reports (id, service_log_id, owner_id, reason, created_at, updated_at, sync_status) VALUES ('oc-1', 'log-1', '$placeholder', 'ABOVE_MARKET_RATE', '$now', '$now', 'SYNCED')")
        driver.exec("INSERT INTO vehicle_catalog_submissions (id, owner_id, make, model, created_at, updated_at, sync_status) VALUES ('vc-1', '$placeholder', 'Tata', 'Sierra', '$now', '$now', 'SYNCED')")
        return db to driver
    }

    /** One row per table for a *different* account, as a failed sign-out would leave them. */
    private fun JdbcSqliteDriver.seedOtherAccount() {
        exec("INSERT INTO profiles (id, full_name, created_at, updated_at, sync_status) VALUES ('$other', 'Priya', '$now', '$now', 'SYNCED')")
        exec("INSERT INTO cars (id, owner_id, make, model, year, fuel_type, current_odometer_km, created_at, updated_at, sync_status) VALUES ('other-car', '$other', 'Honda', 'City', 2021, 'PETROL', 12000, '$now', '$now', 'SYNCED')")
        exec("INSERT INTO service_logs (id, car_id, owner_id, service_date, odometer_km, total_amount_paise, source, created_at, updated_at, sync_status) VALUES ('other-log', 'other-car', '$other', '2026-02-15', 12000, 190000, 'MANUAL', '$now', '$now', 'SYNCED')")
        exec("INSERT INTO documents (id, car_id, owner_id, doc_type, storage_path, doc_source, created_at, updated_at, sync_status) VALUES ('other-doc', 'other-car', '$other', 'INSURANCE', 'docs/other.pdf', 'UPLOADED', '$now', '$now', 'SYNCED')")
        exec("INSERT INTO health_scores (id, car_id, owner_id, score, maintenance_pts, documentation_pts, cost_efficiency_pts, history_pts, algo_version, computed_at, created_at, updated_at, sync_status) VALUES ('other-hs', 'other-car', '$other', 60, 20, 18, 12, 10, 'rule-v1', '$now', '$now', '$now', 'SYNCED')")
        exec("INSERT INTO overcharge_reports (id, service_log_id, owner_id, reason, created_at, updated_at, sync_status) VALUES ('other-oc', 'other-log', '$other', 'ABOVE_MARKET_RATE', '$now', '$now', 'SYNCED')")
        exec("INSERT INTO vehicle_catalog_submissions (id, owner_id, make, model, created_at, updated_at, sync_status) VALUES ('other-vc', '$other', 'Honda', 'City', '$now', '$now', 'SYNCED')")
        exec("INSERT INTO trips (id, car_id, owner_id, started_at, ended_at, distance_m, estimated_m, mode, status, created_at, updated_at, sync_status) VALUES ('other-trip', 'other-car', '$other', '$now', '$now', 4100, 4100, 'DRIVING', 'RECORDED', '$now', '$now', 'SYNCED')")
    }

    private fun JdbcSqliteDriver.exec(sql: String) = execute(null, sql, 0)

    private fun JdbcSqliteDriver.count(sql: String): Int =
        executeQuery(null, sql, { cursor -> app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) cursor.getLong(0)!!.toInt() else 0) }, 0).value

    private fun JdbcSqliteDriver.text(sql: String): String? =
        executeQuery(null, sql, { cursor -> app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) }, 0).value

    /** Owner and status of every row, as one comparable string. */
    private fun JdbcSqliteDriver.snapshot(): String =
        listOf("cars", "service_logs", "documents", "health_scores", "overcharge_reports")
            .joinToString(";") { table ->
                "$table=" + text("SELECT GROUP_CONCAT(id || ':' || owner_id || ':' || sync_status) FROM $table")
            } + ";profiles=" + text("SELECT GROUP_CONCAT(id || ':' || sync_status) FROM profiles")
}
