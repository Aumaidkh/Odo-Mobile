package com.hopcape.odo.core.data.entitlement

import com.hopcape.odo.core.domain.document.entitlement.DocumentAllowance
import com.hopcape.odo.core.domain.document.entitlement.DocumentLimit
import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.ProFeature
import com.hopcape.odo.core.domain.entitlement.Quota
import kotlinx.coroutines.flow.first

/**
 * The vault's cap, read from the owner's plan.
 *
 * The [DocumentAllowance] port stays exactly as it was, so the two use cases that enforce the
 * cap did not change. What changed is where the number comes from: `PlanLimits` instead of a
 * constant in this layer, which is why the free tier's size now lives next to the price it
 * belongs to.
 *
 * Takes the first emission rather than observing. The callers ask once, immediately before a
 * write, which is the only moment the answer matters.
 */
internal class EntitlementDocumentAllowance(
    private val entitlements: EntitlementSource,
) : DocumentAllowance {

    override suspend fun current(): DocumentLimit =
        when (val quota = entitlements.observe().first().quotaFor(ProFeature.DOCUMENTS)) {
            Quota.Unlimited -> DocumentLimit.Unlimited
            is Quota.UpTo -> DocumentLimit.UpTo(quota.max)
            // No plan denies the vault outright today. If one ever does, "you may keep none"
            // is the faithful reading of it.
            Quota.None -> DocumentLimit.UpTo(0)
        }
}
