package com.hopcape.odo.core.domain.entitlement

/** Which plan the owner is on. */
enum class Plan {

    /** No subscription. */
    FREE,

    /** Odo Pro, including a free trial and the grace period after a failed renewal. */
    PRO,
}

/**
 * What the owner is entitled to right now.
 *
 * Callers ask this rather than the plan. `entitlements.has(RECORD_EXPORT)` says what the code
 * is doing; `plan == Plan.PRO` says what someone paid, which is the same thing today and will
 * not be the first time there is a second tier or a feature sold on its own.
 *
 * A grace period reads as [Plan.PRO]. A renewal that failed is a payment problem, and taking
 * the vault away while the owner fixes their card would punish them for it. The profile card
 * is where the warning goes.
 */
data class Entitlements(val plan: Plan) {

    /** Whether [feature] is available at all on this plan. */
    fun has(feature: ProFeature): Boolean = quotaFor(feature).isGranted

    /** How much of [feature] this plan permits. */
    fun quotaFor(feature: ProFeature): Quota = PlanLimits.quota(plan, feature)

    companion object {

        /**
         * What to assume before anything has been read.
         *
         * Free, not Pro. An entitlement that has not loaded is one the app cannot prove, and
         * showing a paywall to someone who paid is a complaint, while unlocking Pro for
         * everyone who is offline is a business.
         */
        val Unknown = Entitlements(Plan.FREE)
    }
}
