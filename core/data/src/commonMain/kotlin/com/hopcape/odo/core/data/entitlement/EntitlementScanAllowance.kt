package com.hopcape.odo.core.data.entitlement

import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.ProFeature
import com.hopcape.odo.core.domain.entitlement.Quota
import com.hopcape.odo.core.domain.scan.entitlement.ScanAllowance
import com.hopcape.odo.core.domain.scan.entitlement.ScanLimit
import com.hopcape.odo.core.domain.scan.entitlement.ScanCredits
import com.hopcape.odo.core.domain.scan.entitlement.ScanUsage
import kotlinx.coroutines.flow.first

/**
 * The scanner's lifetime cap (#248), read from the owner's plan.
 *
 * Two sources, because they are two different facts: the cap comes from the owner's plan
 * via `PlanLimits`, and the tally comes from this device. Extraction runs on the phone, so
 * the phone is the only thing that ever sees a scan happen.
 *
 * That the count is device-local is what makes it survivable to be wrong: a reinstall clears
 * it. Accepted deliberately — the cap limits a feature that costs nothing to run.
 */
internal class EntitlementScanAllowance(
    private val entitlements: EntitlementSource,
    private val usage: ScanUsage,
    private val credits: ScanCredits,
) : ScanAllowance {

    override suspend fun current(): ScanLimit =
        when (val quota = entitlements.observe().first().quotaFor(ProFeature.BILL_SCANS)) {
            Quota.Unlimited -> ScanLimit.Unlimited
            // Bought checks ride alongside the free cap rather than raising it: an owner who
            // paid for three should see three they paid for, not a cap that quietly grew.
            is Quota.UpTo -> ScanLimit.UpTo(
                max = quota.max,
                used = usage.used(),
                bought = credits.available(),
            )
            // No plan refuses scanning outright today. If one ever does, a cap of zero says so
            // through the same type the callers already read.
            Quota.None -> ScanLimit.UpTo(max = 0, used = 0)
        }
}
