package com.hopcape.odo.infrastructure.database.owner

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 12.sqm rebuilds `profiles` to drop `onboarding_goal`, because SQLite 3.18 cannot drop a
 * column in place.
 *
 * A rebuild copies rows by name into a new table, and the failure mode is silent: get the
 * column list wrong and every value after the dropped one shifts a slot, so a city lands in
 * an email. Nothing else catches that — the app compiles either way.
 */
class ProfileGoalDropMigrationTest {

    private lateinit var driver: JdbcSqliteDriver

    private fun value(column: String): String? = driver.executeQuery(
        identifier = null,
        sql = "SELECT $column FROM profiles WHERE id = 'owner-1'",
        mapper = { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null)
        },
        parameters = 0,
    ) {}.value

    /**
     * `profiles` exactly as version 12 declared it. Built by hand rather than by running the
     * migrations from 1: those expect every other table to exist too, and 12.sqm touches only
     * this one.
     */
    private fun createVersion12Profiles(target: JdbcSqliteDriver) = target.execute(
        identifier = null,
        sql = """
            CREATE TABLE profiles (
                id                      TEXT NOT NULL PRIMARY KEY,
                full_name               TEXT,
                onboarding_goal         TEXT,
                onboarding_completed_at TEXT,
                city                    TEXT,
                email                   TEXT,
                avatar_path             TEXT,
                shares_prices           INTEGER NOT NULL DEFAULT 1,
                created_at              TEXT NOT NULL,
                updated_at              TEXT NOT NULL,
                deleted_at              TEXT,
                remote_version          TEXT,
                sync_status             TEXT NOT NULL DEFAULT 'PENDING',
                phone                   TEXT,
                restriction             TEXT NOT NULL DEFAULT 'none'
            )
        """.trimIndent(),
        parameters = 0,
    )

    @Test
    fun anUpgradedInstallKeepsEveryOtherColumnAgainstItsOwnValue() = runTest {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createVersion12Profiles(driver)
        driver.execute(
            identifier = null,
            sql = """
                INSERT INTO profiles (
                    id, full_name, onboarding_goal, onboarding_completed_at, city, email,
                    avatar_path, shares_prices, created_at, updated_at, deleted_at,
                    remote_version, sync_status, phone, restriction
                ) VALUES (
                    'owner-1', 'Rahul', 'SELL_SOON', '2026-01-01T00:00:00Z', 'Pune',
                    'r@example.com', 'avatars/r.png', 1, '2026-01-01T00:00:00Z',
                    '2026-02-01T00:00:00Z', NULL, 'v1', 'SYNCED', '+919876543210', 'none'
                )
            """.trimIndent(),
            parameters = 0,
        )

        OdoDatabase.Schema.migrate(driver, oldVersion = 12L, newVersion = 13L).await()

        // Each value against its own column. A shifted copy passes a row count and fails here.
        assertEquals("Rahul", value("full_name"))
        assertEquals("2026-01-01T00:00:00Z", value("onboarding_completed_at"))
        assertEquals("Pune", value("city"))
        assertEquals("r@example.com", value("email"))
        assertEquals("avatars/r.png", value("avatar_path"))
        assertEquals("1", value("shares_prices"))
        assertEquals("2026-01-01T00:00:00Z", value("created_at"))
        assertEquals("2026-02-01T00:00:00Z", value("updated_at"))
        assertEquals("v1", value("remote_version"))
        assertEquals("SYNCED", value("sync_status"))
        assertEquals("+919876543210", value("phone"))
        assertEquals("none", value("restriction"))
    }

    @Test
    fun theGoalColumnIsGoneAfterTheUpgrade() = runTest {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createVersion12Profiles(driver)

        OdoDatabase.Schema.migrate(driver, oldVersion = 12L, newVersion = 13L).await()

        assertFalse("onboarding_goal" in columnsOf(driver), "columns were: ${columnsOf(driver)}")
    }

    /** A fresh install builds from Profile.sq; an upgrade from the .sqm. They must agree. */
    @Test
    fun aFreshInstallAndAnUpgradeAgreeOnTheColumns() = runTest {
        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(fresh).await()
        val upgraded = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createVersion12Profiles(upgraded)
        OdoDatabase.Schema.migrate(upgraded, oldVersion = 12L, newVersion = 13L).await()

        assertEquals(columnsOf(fresh), columnsOf(upgraded))
    }

    private fun columnsOf(target: JdbcSqliteDriver): String = target.executeQuery(
        identifier = null,
        sql = "SELECT group_concat(name) FROM pragma_table_info('profiles')",
        mapper = { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getString(0).orEmpty() else "")
        },
        parameters = 0,
    ) {}.value
}
