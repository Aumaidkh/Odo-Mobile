package com.hopcape.odo.core.domain.record.entitlement

/**
 * Record exports the owner has bought one at a time and not yet spent (#246).
 *
 * Growth Plan v3 sells the record PDF two ways: inside Pro, or ₹99 once for someone who
 * does not want a subscription. Someone selling their car wants the PDF once and will never
 * want a plan, and today the only thing the app can say to them is "subscribe".
 *
 * **This is a balance, not a plan.** Owning "one export" is counted, spent and buyable
 * again, which is why it cannot ride on
 * [EntitlementSource][com.hopcape.odo.core.domain.entitlement.EntitlementSource] the way
 * every other gate does — that answers what someone *is*, and this answers what they *have
 * left*. It is closer to how `ScanUsage` counts than to how `Entitlements` answers.
 *
 * **Read, never written here.** A balance is granted by honouring a purchase — see
 * [PurchaseGrants][com.hopcape.odo.core.domain.subscription.PurchaseGrants].
 *
 * A credit is spent at the same moment the free allowance is: when the rendered PDF reaches
 * the share sheet. That is stated in the purchase copy so nothing is discovered afterwards —
 * one PDF, not the record as it stands that day.
 */
interface ExportCredits {

    /** Bought and unspent. Zero for anyone who has never bought one. */
    suspend fun available(): Int

    /**
     * Spend one, if there is one.
     *
     * Returns whether a credit was actually taken, so the caller never has to read then
     * write and race itself. False means the balance was already empty.
     */
    suspend fun spend(): Boolean
}
