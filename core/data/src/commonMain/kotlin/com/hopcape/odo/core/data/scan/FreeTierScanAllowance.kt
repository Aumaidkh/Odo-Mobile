package com.hopcape.odo.core.data.scan

import com.hopcape.odo.core.domain.scan.entitlement.ScanAllowance
import com.hopcape.odo.core.domain.scan.entitlement.ScanLimit

/**
 * The [ScanAllowance] in force while nothing sells a subscription: everyone is on the free
 * tier, so everyone gets [FREE_TIER_SCANS] AI scans a month.
 *
 * [used] is reported as zero, and that is the part to be careful about. Nothing on the device
 * counts scans, because the count that matters is the server's — the Edge Function checks
 * `ai_usage` before it calls Anthropic (TDD §7.5), and a client-side tally is a number an
 * attacker edits. Reporting zero here therefore means "the client does not know yet", not
 * "you have all three left", and the quota pill is a hint rather than a promise until the
 * real adapter reads the server's figure.
 *
 * The alternative — counting locally — would be worse than useless: it would disagree with
 * the server the first time the owner used a second device, and the owner would trust the
 * wrong one.
 */
internal class FreeTierScanAllowance : ScanAllowance {

    override suspend fun current(): ScanLimit = FREE_TIER

    private companion object {
        /** PRD §pricing — free tier gets 3 scans a month; Pro is unlimited. */
        const val FREE_TIER_SCANS = 3
        val FREE_TIER = ScanLimit.UpTo(max = FREE_TIER_SCANS, used = 0)
    }
}
