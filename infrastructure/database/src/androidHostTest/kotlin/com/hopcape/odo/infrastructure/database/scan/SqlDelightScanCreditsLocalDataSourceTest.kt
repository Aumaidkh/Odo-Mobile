package com.hopcape.odo.infrastructure.database.scan

import com.hopcape.odo.infrastructure.database.sync.inMemoryDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The bought-bill-check balance, against real SQLite.
 *
 * Money reaches this table and nothing else undoes a mistake in it: a credit that fails to
 * land is a purchase the owner paid for and did not get, and one that fails to leave is a
 * check they keep spending forever.
 */
class SqlDelightScanCreditsLocalDataSourceTest {

    @Test
    fun `an owner who has never bought a pack has none`() = runTest {
        assertEquals(0, local().remaining())
    }

    @Test
    fun `a pack grants all of its checks at once`() = runTest {
        val local = local()

        local.grant(3)

        assertEquals(3, local.remaining())
    }

    @Test
    fun `packs add up rather than replacing each other`() = runTest {
        val local = local()

        local.grant(3)
        local.grant(1)

        assertEquals(4, local.remaining())
    }

    @Test
    fun `spending takes one and says it did`() = runTest {
        val local = local()
        local.grant(2)

        assertTrue(local.spend())
        assertEquals(1, local.remaining())
    }

    /**
     * The balance can never go negative, however many scans finish at once. Without the
     * guard on the update, two scans racing each other would both take the last check.
     */
    @Test
    fun `spending an empty balance takes nothing and says so`() = runTest {
        val local = local()
        local.grant(1)

        assertTrue(local.spend())
        assertFalse(local.spend())
        assertEquals(0, local.remaining())
    }

    /** A refund or a bad count must not be able to write a balance nobody can spend. */
    @Test
    fun `granting nothing is a no-op`() = runTest {
        val local = local()
        local.grant(3)

        local.grant(0)
        local.grant(-5)

        assertEquals(3, local.remaining())
    }

    private fun local(): SqlDelightScanCreditsLocalDataSource {
        val (database, _) = inMemoryDatabase()
        return SqlDelightScanCreditsLocalDataSource(database)
    }
}
