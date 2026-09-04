package com.hopcape.odo.infrastructure.database.scan

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SQL behaviour for [SqlDelightScanUsageLocalDataSource] — the insert-then-update idiom, and
 * the upgrade path, which is the part that could break a phone rather than a build.
 */
class SqlDelightScanUsageLocalDataSourceTest {

    private fun newDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private fun local(db: OdoDatabase) = SqlDelightScanUsageLocalDataSource(database = db)

    @Test
    fun aMonthNothingWasScannedInCountsZero() = runTest {
        assertEquals(0, local(newDb()).countFor("2026-08"))
    }

    @Test
    fun theFirstScanOfAMonthStartsItAtOne() = runTest {
        val local = local(newDb())

        local.increment("2026-08")

        assertEquals(1, local.countFor("2026-08"))
    }

    @Test
    fun laterScansAddToTheSameMonth() = runTest {
        val local = local(newDb())

        repeat(3) { local.increment("2026-08") }

        assertEquals(3, local.countFor("2026-08"))
    }

    @Test
    fun eachMonthCountsOnItsOwn() = runTest {
        val local = local(newDb())

        repeat(3) { local.increment("2026-08") }
        local.increment("2026-09")

        assertEquals(3, local.countFor("2026-08"))
        assertEquals(1, local.countFor("2026-09"), "a new month starts again, it does not inherit")
    }

    /**
     * What an existing 1.0 install does on first launch after the update.
     *
     * Its database is at version 1 and has never seen `scan_usage`, so the table has to come
     * from the migration rather than from the `.sq` file — a `.sq` alone would leave that
     * phone querying a table it never created. This is the first migration the database has
     * had, so the path itself is new, not just its contents.
     */
    @Test
    fun anExistingInstallGetsTheTableFromTheMigration() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        OdoDatabase.Schema.migrate(driver, oldVersion = 1L, newVersion = 2L).await()

        assertEquals(0, local(OdoDatabase(driver)).countFor("2026-08"))
    }

    @Test
    fun theSchemaSaysItIsAtTheMigratedVersion() {
        assertEquals(
            16,
            OdoDatabase.Schema.version,
            "adding a migration file bumps this; an install upgrades only if it does",
        )
    }
}
