package com.hopcape.odo.core.data.scan

import com.hopcape.odo.core.domain.scan.entitlement.ScanAllowance
import com.hopcape.odo.core.domain.scan.entitlement.ScanCredits
import com.hopcape.odo.core.domain.scan.entitlement.ScanLimit
import com.hopcape.odo.core.domain.scan.entitlement.ScanUsage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Free scans first, bought ones after.
 *
 * The order is the whole rule, and getting it backwards is not visible from any screen: the
 * owner simply runs out earlier than they paid to.
 */
class AllowanceScanChargerTest {

    @Test
    fun `a scan with free ones left is charged to the free tally`() = runTest {
        val usage = FakeUsage(used = 2)
        val credits = FakeCredits(available = 3)

        charger(limit(max = 5, used = 2, bought = 3), usage, credits).chargeOne()

        assertEquals(3, usage.recorded)
        assertEquals(3, credits.available, "a bought check must not pay for a free scan")
    }

    @Test
    fun `a scan with none left takes a bought one`() = runTest {
        val usage = FakeUsage(used = 5)
        val credits = FakeCredits(available = 3)

        charger(limit(max = 5, used = 5, bought = 3), usage, credits).chargeOne()

        assertEquals(2, credits.available)
        assertEquals(5, usage.recorded, "the free tally is already spent and must not move")
    }

    /**
     * Nothing left anywhere. The scan still happened, and the tally is what says so — a scan
     * that goes uncounted is one the cap will let through again.
     */
    @Test
    fun `a scan with nothing left is still counted`() = runTest {
        val usage = FakeUsage(used = 5)
        val credits = FakeCredits(available = 0)

        charger(limit(max = 5, used = 5, bought = 0), usage, credits).chargeOne()

        assertEquals(6, usage.recorded)
        assertEquals(0, credits.available)
    }

    /** An uncapped plan has nothing to run out of, so there is nothing to fall back to. */
    @Test
    fun `an unlimited plan never spends a bought check`() = runTest {
        val usage = FakeUsage(used = 40)
        val credits = FakeCredits(available = 3)

        charger(ScanLimit.Unlimited, usage, credits).chargeOne()

        assertEquals(41, usage.recorded)
        assertEquals(3, credits.available)
    }

    /* ------------------------------ Fixtures ------------------------------ */

    private fun charger(limit: ScanLimit, usage: ScanUsage, credits: ScanCredits) =
        AllowanceScanCharger(allowance = { limit }, usage = usage, credits = credits)

    private fun limit(max: Int, used: Int, bought: Int) = ScanLimit.UpTo(max, used, bought)

    private class FakeUsage(used: Int) : ScanUsage {
        var recorded = used
            private set

        override suspend fun used(): Int = recorded
        override suspend fun recordScan() { recorded++ }
    }

    private class FakeCredits(var available: Int) : ScanCredits {
        override suspend fun available(): Int = available
        override suspend fun spend(): Boolean =
            if (available > 0) { available--; true } else false
    }
}
