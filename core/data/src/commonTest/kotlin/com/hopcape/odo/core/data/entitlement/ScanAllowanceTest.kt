package com.hopcape.odo.core.data.entitlement

import com.hopcape.odo.core.domain.entitlement.Entitlements
import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.Plan
import com.hopcape.odo.core.domain.scan.entitlement.ScanLimit
import com.hopcape.odo.core.domain.scan.entitlement.ScanUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The scanner's cap: the plan's number against this device's tally, and nothing else.
 *
 * `bought` is asserted at zero on every case. It is the field that carried the bill-check
 * balance into this cap, and since no charger spends a check for a scan it never came back
 * down — one bought check uncapped the scanner permanently.
 */
class ScanAllowanceTest {

    @Test
    fun `the free plan opens with five scans`() = runTest {
        val limit = allowance(scansUsed = 0).current()

        assertTrue(limit.allowsAnother)
        assertEquals(5, limit.remaining)
        assertEquals(ScanLimit.UpTo(max = 5, used = 0, bought = 0), limit)
    }

    @Test
    fun `five scans close the wall`() = runTest {
        val limit = allowance(scansUsed = 5).current()

        assertFalse(limit.allowsAnother)
        assertEquals(0, limit.remaining)
        assertEquals(ScanLimit.UpTo(max = 5, used = 5, bought = 0), limit)
    }

    /**
     * Nothing sells a scan one at a time, so the cap can never report one as bought. The
     * bill-check balance is a different product and a different pot.
     */
    @Test
    fun `nothing is ever counted as a bought scan`() = runTest {
        val limit = allowance(scansUsed = 2).current()

        assertEquals(ScanLimit.UpTo(max = 5, used = 2, bought = 0), limit)
        assertEquals(3, limit.remaining, "three free scans left and no more")
    }

    @Test
    fun `pro has no cap`() = runTest {
        val limit = EntitlementScanAllowance(
            entitlements = SourceOf(Plan.PRO),
            usage = FakeScanUsage(used = 99),
        ).current()

        assertEquals(ScanLimit.Unlimited, limit)
    }

    /* ------------------------------ Fixtures ------------------------------ */

    private fun allowance(scansUsed: Int) = EntitlementScanAllowance(
        entitlements = SourceOf(Plan.FREE),
        usage = FakeScanUsage(used = scansUsed),
    )

    private class SourceOf(private val plan: Plan) : EntitlementSource {
        override fun observe(): Flow<Entitlements> = flowOf(Entitlements(plan))
        override suspend fun refresh() = Unit
    }

    private class FakeScanUsage(used: Int) : ScanUsage {
        var recorded = used
            private set

        override suspend fun used(): Int = recorded
        override suspend fun recordScan() { recorded++ }
    }
}
