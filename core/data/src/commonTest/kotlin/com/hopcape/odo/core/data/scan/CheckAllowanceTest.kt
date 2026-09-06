package com.hopcape.odo.core.data.scan

import com.hopcape.odo.core.domain.entitlement.Entitlements
import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.Plan
import com.hopcape.odo.core.domain.scan.entitlement.CheckUsage
import com.hopcape.odo.core.domain.scan.entitlement.ScanCredits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The bill check's own balance.
 *
 * The whole reason it exists: it shared the scanner's, so five scanned bills closed the check
 * behind a wall the owner had never spent anything on — and scanning is the thing the app most
 * wants them to do.
 */
class CheckAllowanceTest {

    @Test
    fun `scanning bills does not spend a check`() = runTest {
        // The scan tally is not even a dependency here, which is the point: nothing the
        // scanner does can reach this balance.
        val allowance = allowance(checksUsed = 0)

        val limit = allowance.current()

        assertTrue(limit.allowsAnother)
        assertEquals(5, limit.remaining)
    }

    @Test
    fun `five checks close the wall`() = runTest {
        val limit = allowance(checksUsed = 5).current()

        assertFalse(limit.allowsAnother)
        assertEquals(0, limit.remaining)
    }

    /** A pack is checks. It is what the product is called, and now what it buys. */
    @Test
    fun `a bought pack opens it again`() = runTest {
        val limit = allowance(checksUsed = 5, bought = 3).current()

        assertTrue(limit.allowsAnother)
        assertEquals(3, limit.remaining)
    }

    @Test
    fun `a free one is spent before a bought one`() = runTest {
        val usage = FakeCheckUsage(used = 2)
        val credits = FakeCredits(available = 3)

        charger(usage, credits, max = 5).chargeOne()

        assertEquals(3, usage.recorded)
        assertEquals(3, credits.available, "a bought check must not pay for a free one")
    }

    @Test
    fun `a bought one is spent once the free ones are gone`() = runTest {
        val usage = FakeCheckUsage(used = 5)
        val credits = FakeCredits(available = 3)

        charger(usage, credits, max = 5).chargeOne()

        assertEquals(2, credits.available)
        assertEquals(5, usage.recorded, "the free tally is already spent and must not move")
    }

    /* ------------------------------ Fixtures ------------------------------ */

    private fun allowance(checksUsed: Int, bought: Int = 0) = EntitlementCheckAllowance(
        entitlements = FreeSource,
        usage = FakeCheckUsage(used = checksUsed),
        credits = FakeCredits(available = bought),
    )

    private fun charger(usage: CheckUsage, credits: ScanCredits, max: Int) =
        AllowanceCheckCharger(
            allowance = EntitlementCheckAllowance(FreeSource, usage, credits),
            usage = usage,
            credits = credits,
        ).also { check(max == 5) { "the free plan's cap, stated once in PlanLimits" } }

    private object FreeSource : EntitlementSource {
        override fun observe(): Flow<Entitlements> = flowOf(Entitlements(Plan.FREE))
        override suspend fun refresh() = Unit
    }

    private class FakeCheckUsage(used: Int) : CheckUsage {
        var recorded = used
            private set

        override suspend fun used(): Int = recorded
        override suspend fun recordCheck() { recorded++ }
    }

    private class FakeCredits(var available: Int) : ScanCredits {
        override suspend fun available(): Int = available
        override suspend fun spend(): Boolean =
            if (available > 0) { available--; true } else false
    }
}
