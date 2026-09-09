package com.hopcape.odo.core.data.scan

import com.hopcape.odo.core.domain.scan.entitlement.ScanCharger
import com.hopcape.odo.core.domain.scan.entitlement.ScanUsage

/**
 * Counts a scan against the scanner's own cap.
 *
 * **It no longer spends a bought credit.** The one-off products are "3 Bill Checks" and "One
 * Bill Check", and their grant's own field is documented as the checks it awards — they were
 * spendable here only because scans and checks shared one balance. Selling a bill check and
 * quietly letting the scanner eat it is the kind of thing an owner finds out by running out
 * of the thing they actually bought.
 *
 * An unlimited plan is charged to the tally rather than to nothing, so "how many has this
 * owner scanned" keeps answering after someone subscribes.
 */
internal class AllowanceScanCharger(
    private val usage: ScanUsage,
) : ScanCharger {

    override suspend fun chargeOne() = usage.recordScan()
}
