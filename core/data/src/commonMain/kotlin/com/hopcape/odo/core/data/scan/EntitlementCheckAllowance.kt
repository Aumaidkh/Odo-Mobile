package com.hopcape.odo.core.data.scan

import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.ProFeature
import com.hopcape.odo.core.domain.entitlement.Quota
import com.hopcape.odo.core.domain.scan.entitlement.CheckAllowance
import com.hopcape.odo.core.domain.scan.entitlement.CheckCharger
import com.hopcape.odo.core.domain.scan.entitlement.CheckUsage
import com.hopcape.odo.core.domain.scan.entitlement.ScanCredits
import com.hopcape.odo.core.domain.scan.entitlement.ScanLimit
import kotlinx.coroutines.flow.first

/**
 * What the plan permits of bill checks, against what has been spent on them.
 *
 * The scanner's twin, reading its own tally. They shared one before, so five scanned bills
 * closed the check — and the owner had spent nothing on it.
 *
 * **The bought credits belong here.** The products are called "3 Bill Checks" and "One Bill
 * Check", and their grant's own field is documented as "bill checks this awards" — they
 * simply landed in the scanner's pot because there was only one. A pack buys checks now, and
 * nothing about it raises the scanner's cap.
 */
internal class EntitlementCheckAllowance(
    private val entitlements: EntitlementSource,
    private val usage: CheckUsage,
    private val credits: ScanCredits,
) : CheckAllowance {

    override suspend fun current(): ScanLimit =
        when (val quota = entitlements.observe().first().quotaFor(ProFeature.BILL_CHECKS)) {
            Quota.Unlimited -> ScanLimit.Unlimited
            is Quota.UpTo -> ScanLimit.UpTo(
                max = quota.max,
                used = usage.used(),
                bought = credits.available(),
            )
            // No plan refuses checking outright today. A cap of zero would say so through the
            // same type — and a check the owner paid for is still theirs either way.
            Quota.None -> ScanLimit.UpTo(max = 0, used = 0, bought = credits.available())
        }
}

/**
 * Spends a check: a free one while any remain, then a bought one.
 *
 * The same order as the scanner's charger, and for the same reason — taking a bought check
 * while a free one is still there sells the owner something they already had.
 */
internal class AllowanceCheckCharger(
    private val allowance: CheckAllowance,
    private val usage: CheckUsage,
    private val credits: ScanCredits,
) : CheckCharger {

    override suspend fun chargeOne() {
        val free = allowance.current().freeRemaining
        // Null is an uncapped plan: nothing to run out of, so nothing to fall back to.
        if (free == null || free > 0) {
            usage.recordCheck()
            return
        }
        // Out of free ones. Take a bought one — and if the balance was empty after all, still
        // count the check, because it happened and the tally is what says so.
        if (!credits.spend()) usage.recordCheck()
    }
}
