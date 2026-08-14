package com.hopcape.odo.core.data.entitlement

import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.ProFeature
import com.hopcape.odo.core.domain.entitlement.Quota
import com.hopcape.odo.core.domain.scan.entitlement.ScanAllowance
import com.hopcape.odo.core.domain.scan.entitlement.ScanLimit
import kotlinx.coroutines.flow.first

/**
 * The scanner's monthly cap, read from the owner's plan.
 *
 * `used` is still reported as zero. Nothing counts scans yet — the extraction runs on the
 * device, so no server sees one go past — and inventing a number here would be worse than
 * admitting there isn't one. S3 of the paywall plan adds the device-local counter this reads
 * instead, and the quota pill is a hint rather than a promise until it does.
 *
 * The cap itself is now real: it comes from `PlanLimits` rather than a constant in this layer.
 */
internal class EntitlementScanAllowance(
    private val entitlements: EntitlementSource,
) : ScanAllowance {

    override suspend fun current(): ScanLimit =
        when (val quota = entitlements.observe().first().quotaFor(ProFeature.BILL_SCANS)) {
            Quota.Unlimited -> ScanLimit.Unlimited
            is Quota.UpTo -> ScanLimit.UpTo(max = quota.max, used = NOT_COUNTED_YET)
            // No plan refuses scanning outright today. If one ever does, a cap of zero says so
            // through the same type the callers already read.
            Quota.None -> ScanLimit.UpTo(max = 0, used = 0)
        }

    private companion object {
        /** Replaced by the device-local monthly count in S3. */
        const val NOT_COUNTED_YET = 0
    }
}
