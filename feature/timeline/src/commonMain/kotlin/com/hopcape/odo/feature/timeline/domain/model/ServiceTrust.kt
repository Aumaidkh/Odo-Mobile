package com.hopcape.odo.feature.timeline.domain.model

import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.shared.Amount

/**
 * The trust state a service node renders — the Odo trust model as three mutually exclusive
 * cases, so the UI's `when` is exhaustive and [Flagged] carries its amount instead of the
 * screen re-reading a nullable field.
 */
internal sealed interface ServiceTrust {
    /** Bill-backed and priced fairly. */
    data object Verified : ServiceTrust

    /** Bill-backed, but the fairness check judged it [overchargedBy] over the average. */
    data class Flagged(val overchargedBy: Amount) : ServiceTrust

    /** No bill attached — nothing to verify or benchmark against (PRD guardrail). */
    data object SelfReported : ServiceTrust
}

/**
 * Derived, never stored. Only bill-backed entries can be fairness-checked, so the cases
 * cannot overlap.
 */
internal val ActivityEvent.Service.trust: ServiceTrust
    get() {
        val over = overchargedBy
        return when {
            over != null -> ServiceTrust.Flagged(over)
            verification == VerificationStatus.VERIFIED -> ServiceTrust.Verified
            else -> ServiceTrust.SelfReported
        }
    }
