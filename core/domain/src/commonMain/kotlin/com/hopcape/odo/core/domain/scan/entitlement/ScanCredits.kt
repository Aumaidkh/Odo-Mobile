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
 * **Read, never written here.** A balance is granted by honouring a purchase — see
 * [PurchaseGrants][com.hopcape.odo.core.domain.subscription.PurchaseGrants] — because the
 * record that a transaction was honoured is the same write as the credit it is worth.
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
     * Spend one, if there is one.
     *
     * Returns whether a credit was actually taken, so the caller never has to read then
     * write and race itself. False means the balance was already empty.
     */
    suspend fun spend(): Boolean
}
