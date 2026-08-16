package com.hopcape.odo.core.domain.entitlement

/**
 * What each plan grants. The single table the whole app gates on.
 *
 * Only the free plan is listed. Pro grants everything, so writing it out would be a second
 * copy of [ProFeature] that can disagree with the first one. Adding a gate is therefore one
 * entry in [ProFeature] and one row in [FREE_PLAN].
 *
 * A feature with no row gets [Quota.None] — denied. That is the safe direction: a row someone
 * forgot to write locks a feature, it never gives it away. `PlanLimitsTest` states the
 * expected quota for every feature in an exhaustive `when`, so adding a [ProFeature] stops
 * that test compiling until the row is written. The omission cannot reach a build.
 *
 * The numbers are here rather than in the data layer because they are pricing, not storage.
 * They were `FreeTierDocumentAllowance.FREE_TIER_LIMIT` and `FreeTierScanAllowance
 * .FREE_TIER_SCANS`, in two files that had no way to know about each other.
 */
object PlanLimits {

    /**
     * The free plan (Growth Plan v3, #244): three documents in the vault, five scans ever,
     * and neither the health-score breakdown nor the record export.
     *
     * **What is deliberately absent.** Growth Plan v3 names three things that must never be
     * gated, and the way to keep a promise like that is to make breaking it visible:
     *
     * - **Reminders** (#249). They are the retention engine and the affiliate engine both —
     *   the insurance/PUC referral revenue hangs off the expiry reminder, so a gated
     *   reminder is a gated referral. `PlanLimitsTest` fails if a reminder-shaped entry
     *   appears in [ProFeature], which is the only thing standing between this rule and
     *   someone in a hurry six months from now.
     * - **Auto odometer** and **refuel logging**, automatic channel included. They are the
     *   habit engines: capping them stops the habit forming for exactly the owners who have
     *   not paid yet, which are the ones the habit was meant to convert. Detection used to
     *   be `SMART_REFUEL_DETECT to Quota.UpTo(10)` and the cap was enforced by releasing the
     *   notification-listener binding — an owner who granted the most sensitive permission
     *   on the phone quietly stopped getting what they granted it for. Removed in #251.
     */
    private val FREE_PLAN: Map<ProFeature, Quota> = mapOf(
        ProFeature.DOCUMENTS to Quota.UpTo(3),
        // Lifetime, not monthly (#248). Three a month never fired: an owner services a car
        // three or four times a *year*, so the cap existed and was never reached. The fix is
        // the period rather than the number — five is the first service, the RC and the
        // insurance, which is a real taste that runs out inside the first year.
        ProFeature.BILL_SCANS to Quota.UpTo(5),
        ProFeature.HEALTH_BREAKDOWN to Quota.None,
        // Lifetime, for the same reason: an owner exports a record when they are selling the
        // car or handing it to a workshop, which is a handful of times ever.
        ProFeature.RECORD_EXPORT to Quota.UpTo(3),
    )

    /** What [plan] permits of [feature]. */
    fun quota(plan: Plan, feature: ProFeature): Quota = when (plan) {
        Plan.PRO -> Quota.Unlimited
        Plan.FREE -> FREE_PLAN[feature] ?: Quota.None
    }
}
