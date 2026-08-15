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
     * The free plan. PRD §pricing: three documents in the vault, three scans a month, and
     * neither the health-score breakdown nor the record export.
     */
    private val FREE_PLAN: Map<ProFeature, Quota> = mapOf(
        ProFeature.DOCUMENTS to Quota.UpTo(3),
        ProFeature.BILL_SCANS to Quota.UpTo(3),
        ProFeature.HEALTH_BREAKDOWN to Quota.None,
        ProFeature.RECORD_EXPORT to Quota.None,
    )

    /** What [plan] permits of [feature]. */
    fun quota(plan: Plan, feature: ProFeature): Quota = when (plan) {
        Plan.PRO -> Quota.Unlimited
        Plan.FREE -> FREE_PLAN[feature] ?: Quota.None
    }
}
