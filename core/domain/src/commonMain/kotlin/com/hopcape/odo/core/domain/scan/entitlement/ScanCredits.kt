package com.hopcape.odo.core.domain.scan.entitlement

/**
 * Bill checks the owner has bought one at a time and not yet spent.
 *
 * **A balance, not a plan.** Owning "three checks" is counted, spent and buyable again,
 * which is why it cannot ride on
 * [EntitlementSource][com.hopcape.odo.core.domain.entitlement.EntitlementSource] the way
 * every other gate does — that answers what someone *is*, and this answers what they *have
 * left*. It is closer to how [ScanUsage] counts than to how `Entitlements` answers, and the
 * same shape as
 * [ExportCredits][com.hopcape.odo.core.domain.record.entitlement.ExportCredits].
 *
 * Spent only once the free allowance is gone — see
 * [ScanCharger][com.hopcape.odo.core.domain.scan.entitlement.ScanCharger]. Charging a
 * bought check while a free one is still available would sell the owner something they
 * already had.
 */
interface ScanCredits {

    /** Bought and unspent. Zero for anyone who has never bought a pack. */
    suspend fun available(): Int

    /**
     * Record a completed purchase of [count] checks.
     *
     * A count rather than one call per check, because the packs sell one and three and the
     * balance is a single number — three calls would be three chances to crash halfway.
     */
    suspend fun grant(count: Int)

    /**
     * Spend one, if there is one.
     *
     * Returns whether a credit was actually taken, so the caller never has to read then
     * write and race itself. False means the balance was already empty.
     */
    suspend fun spend(): Boolean
}
