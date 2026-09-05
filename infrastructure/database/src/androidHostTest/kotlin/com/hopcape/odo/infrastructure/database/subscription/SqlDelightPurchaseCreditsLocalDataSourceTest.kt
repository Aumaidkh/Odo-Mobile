package com.hopcape.odo.infrastructure.database.subscription

import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.subscription.CreditKind
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.inMemoryDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The bought balance, against real SQLite.
 *
 * Money reaches these tables and nothing else undoes a mistake in them: a credit that fails
 * to land is a purchase the owner paid for and did not get, and one that can be claimed twice
 * is a check nobody paid for.
 */
class SqlDelightPurchaseCreditsLocalDataSourceTest {

    @Test
    fun `an owner who has bought nothing has nothing`() = runTest {
        val local = local()

        assertEquals(0, local.available(CreditKind.BILL_CHECK))
        assertEquals(0, local.available(CreditKind.RECORD_EXPORT))
    }

    @Test
    fun `a claim credits what it was worth`() = runTest {
        val local = local()

        assertTrue(local.claim("txn-1", scanChecks = 3, recordExports = 0))

        assertEquals(3, local.available(CreditKind.BILL_CHECK))
        assertEquals(0, local.available(CreditKind.RECORD_EXPORT), "the other balance is untouched")
    }

    /**
     * The store reports a completed purchase on every launch and every customer-info refresh.
     * The second claim is what a reinstall used to turn into a second credit.
     */
    @Test
    fun `the same transaction is claimed once and credits once`() = runTest {
        val local = local()

        assertTrue(local.claim("txn-1", scanChecks = 3, recordExports = 0))
        assertFalse(local.claim("txn-1", scanChecks = 3, recordExports = 0))
        assertFalse(local.claim("txn-1", scanChecks = 3, recordExports = 0))

        assertEquals(3, local.available(CreditKind.BILL_CHECK))
    }

    /** Two packs are two transactions, and the product alone could not tell them apart. */
    @Test
    fun `two transactions add up`() = runTest {
        val local = local()

        local.claim("txn-1", scanChecks = 3, recordExports = 0)
        local.claim("txn-2", scanChecks = 1, recordExports = 0)

        assertEquals(4, local.available(CreditKind.BILL_CHECK))
    }

    @Test
    fun `spending takes one and says it did`() = runTest {
        val local = local()
        local.claim("txn-1", scanChecks = 2, recordExports = 0)

        assertTrue(local.spend(CreditKind.BILL_CHECK))

        assertEquals(1, local.available(CreditKind.BILL_CHECK))
    }

    /**
     * The balance can never go below zero, however many shares or scans finish at once.
     * Without the guarded read-and-insert, two spends racing would both take the last one.
     */
    @Test
    fun `spending an empty balance takes nothing and says so`() = runTest {
        val local = local()
        local.claim("txn-1", scanChecks = 1, recordExports = 0)

        assertTrue(local.spend(CreditKind.BILL_CHECK))
        assertFalse(local.spend(CreditKind.BILL_CHECK))

        assertEquals(0, local.available(CreditKind.BILL_CHECK))
    }

    /** One purchase, one kind. Spending an export must not touch the bill checks. */
    @Test
    fun `the two balances are spent separately`() = runTest {
        val local = local()
        local.claim("txn-1", scanChecks = 2, recordExports = 1)

        assertTrue(local.spend(CreditKind.RECORD_EXPORT))

        assertEquals(2, local.available(CreditKind.BILL_CHECK))
        assertEquals(0, local.available(CreditKind.RECORD_EXPORT))
    }

    /**
     * What a reinstall now looks like: the rows come back from the server, and the store's
     * report of the same purchase is recognised rather than honoured a second time. Spends
     * come back too, so the balance is what was left rather than the whole pack.
     */
    @Test
    fun `a restored owner keeps what is left and cannot claim it again`() = runTest {
        val first = local()
        first.claim("txn-1", scanChecks = 3, recordExports = 0)
        first.spend(CreditKind.BILL_CHECK)

        // A fresh install, then the pull: the same rows, applied as the sync engine applies
        // them, and then the reconciler asking to honour the purchase it read from the store.
        val (database, _) = inMemoryDatabase()
        val restored = local(database)
        database.purchaseCreditsQueries.insertClaimFromRemote(
            id = "claim-1",
            owner_id = OWNER,
            transaction_id = "txn-1",
            scan_checks = 3,
            record_exports = 0,
            claimed_at = STAMP,
            created_at = STAMP,
            updated_at = STAMP,
            deleted_at = null,
            remote_version = STAMP,
        )
        database.purchaseCreditsQueries.insertSpendFromRemote(
            id = "spend-1",
            owner_id = OWNER,
            kind = CreditKind.BILL_CHECK.name,
            spent_at = STAMP,
            created_at = STAMP,
            updated_at = STAMP,
            deleted_at = null,
            remote_version = STAMP,
        )

        assertFalse(restored.claim("txn-1", scanChecks = 3, recordExports = 0), "already honoured")
        assertEquals(2, restored.available(CreditKind.BILL_CHECK), "what was left, not the pack")
    }

    /** Every write leaves the row for the outbox to pick up (SYNC_DESIGN §4). */
    @Test
    fun `claims and spends are written pending`() = runTest {
        val (database, _) = inMemoryDatabase()
        val local = local(database)

        local.claim("txn-1", scanChecks = 1, recordExports = 0)
        local.spend(CreditKind.BILL_CHECK)

        assertEquals(1, database.purchaseCreditsQueries.selectPendingClaims().executeAsList().size)
        assertEquals(1, database.purchaseCreditsQueries.selectPendingSpends().executeAsList().size)
    }

    private fun local(
        database: OdoDatabase = inMemoryDatabase().first,
    ): SqlDelightPurchaseCreditsLocalDataSource =
        SqlDelightPurchaseCreditsLocalDataSource(
            database = database,
            ids = IncrementingIds(),
            owners = { OwnerId(OWNER) },
            clock = FixedClock,
        )

    private class IncrementingIds : IdGenerator {
        private var next = 0
        override fun newId(): String = "id-${next++}"
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.parse(STAMP)
    }

    private companion object {
        const val OWNER = "owner-1"
        const val STAMP = "2026-09-04T10:00:00Z"
    }
}
