package com.hopcape.odo.core.data.scan

import com.hopcape.odo.core.domain.scan.entitlement.ScanAllowance
import com.hopcape.odo.core.domain.scan.entitlement.ScanCharger
import com.hopcape.odo.core.domain.scan.entitlement.ScanCredits
import com.hopcape.odo.core.domain.scan.entitlement.ScanLimit
import com.hopcape.odo.core.domain.scan.entitlement.ScanUsage

/**
 * Free scans first, bought ones after.
 *
 * The order is the whole rule: charging a bought check while a free one is still available
 * would sell the owner something they already had, and they would find out by running out
 * early.
 *
 * An unlimited plan is charged to the tally rather than to nothing, so "how many has this
 * owner scanned" keeps answering after someone subscribes. A credit is never taken from a
 * plan that did not need it.
 */
internal class AllowanceScanCharger(
    private val allowance: ScanAllowance,
    private val usage: ScanUsage,
    private val credits: ScanCredits,
) : ScanCharger {

    override suspend fun chargeOne() {
        val free = allowance.current().freeRemaining
        // Null is an uncapped plan: nothing to run out of, so nothing to fall back to.
        if (free == null || free > 0) {
            usage.recordScan()
            return
        }
        // Out of free ones. Take a bought one — and if the balance was empty after all,
        // still count the scan, because it happened and the tally is what says so.
        if (!credits.spend()) usage.recordScan()
    }
}
