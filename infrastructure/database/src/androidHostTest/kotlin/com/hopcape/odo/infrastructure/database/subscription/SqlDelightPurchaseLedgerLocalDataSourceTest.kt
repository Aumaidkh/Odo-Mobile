package com.hopcape.odo.infrastructure.database.subscription

import com.hopcape.odo.infrastructure.database.sync.inMemoryDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The claim-exactly-once rule, against real SQLite.
 *
 * The whole recovery path rests on this answer: the caller awards a purchase only when it is
 * told it claimed it, so a second true is a pack of checks nobody paid for.
 */
class SqlDelightPurchaseLedgerLocalDataSourceTest {

    @Test
    fun `a purchase nobody has claimed is claimed`() = runTest {
        assertTrue(ledger().claim("txn-1"))
    }

    /** The store reports the same purchase on every launch; only the first may be honoured. */
    @Test
    fun `a purchase already claimed is refused`() = runTest {
        val ledger = ledger()

        assertTrue(ledger.claim("txn-1"))
        assertFalse(ledger.claim("txn-1"))
        assertFalse(ledger.claim("txn-1"))
    }

    /** Two purchases of the same pack are two transactions, and both were paid for. */
    @Test
    fun `different transactions are claimed independently`() = runTest {
        val ledger = ledger()

        assertTrue(ledger.claim("txn-1"))
        assertTrue(ledger.claim("txn-2"))
    }

    /**
     * `claimedRows` reads `changes()`, which is per-connection and counts the last statement
     * run on it. Interleaving a second claim between an insert and its count would report the
     * wrong one if the two were not inside a transaction together.
     */
    @Test
    fun `an interleaved claim does not change what the previous one was told`() = runTest {
        val ledger = ledger()
        ledger.claim("txn-1")

        assertTrue(ledger.claim("txn-2"))
        assertFalse(ledger.claim("txn-1"))
        assertTrue(ledger.claim("txn-3"))
    }

    private fun ledger(): SqlDelightPurchaseLedgerLocalDataSource {
        val (database, _) = inMemoryDatabase()
        return SqlDelightPurchaseLedgerLocalDataSource(database, Clock.System)
    }
}
