package com.hopcape.odo.core.data.subscription

import com.hopcape.odo.core.domain.record.entitlement.ExportCredits
import com.hopcape.odo.core.domain.scan.entitlement.ScanCredits
import com.hopcape.odo.core.domain.subscription.OneTimeGrant
import com.hopcape.odo.core.domain.subscription.OneTimeGrants

/**
 * Credits a purchase to whichever balances it names.
 *
 * A grant can name more than one, so this awards every balance rather than branching on the
 * product: adding a bundle later is a row in [OneTimeGrant], not another `when` here.
 */
internal class BalanceOneTimeGrants(
    private val scans: ScanCredits,
    private val exports: ExportCredits,
) : OneTimeGrants {

    override suspend fun award(grant: OneTimeGrant) {
        if (grant.scanChecks > 0) scans.grant(grant.scanChecks)
        // The export balance grants one at a time, so a count becomes that many calls.
        repeat(grant.recordExports) { exports.grant() }
    }
}
