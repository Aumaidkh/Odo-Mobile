package com.hopcape.odo.core.data.scan

import com.hopcape.odo.core.domain.scan.entitlement.ScanUsage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A scan counts against the scanner's cap, and nothing else.
 *
 * It used to fall back to a bought credit when the free ones ran out — which meant a "3 Bill
 * Checks" pack could be eaten by the scanner, and the owner found out by running out of the
 * thing they had actually paid for.
 */
class AllowanceScanChargerTest {

    @Test
    fun `a scan is counted against the scan tally`() = runTest {
        val usage = FakeUsage(used = 2)

        AllowanceScanCharger(usage).chargeOne()

        assertEquals(3, usage.recorded)
    }

    /**
     * Past the cap it is still counted. A scan that goes uncounted is one the cap will let
     * through again.
     */
    @Test
    fun `a scan past the cap is still counted`() = runTest {
        val usage = FakeUsage(used = 5)

        AllowanceScanCharger(usage).chargeOne()

        assertEquals(6, usage.recorded)
    }

    private class FakeUsage(used: Int) : ScanUsage {
        var recorded = used
            private set

        override suspend fun used(): Int = recorded
        override suspend fun recordScan() { recorded++ }
    }
}
