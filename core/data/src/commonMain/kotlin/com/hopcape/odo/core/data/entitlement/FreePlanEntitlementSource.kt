package com.hopcape.odo.core.data.entitlement

import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.Entitlements
import com.hopcape.odo.core.domain.entitlement.Plan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The [EntitlementSource] in force while nothing sells a subscription: everyone is on the
 * free plan.
 *
 * This is the honest answer today, not a placeholder that pretends to read something. There
 * is no subscription state in the app yet, so [Plan.FREE] is true for every owner.
 *
 * `:infrastructure:billing` replaces it with the RevenueCat-backed source in one line of
 * `coreDataModule`. Nothing that reads entitlements changes when it does.
 */
internal class FreePlanEntitlementSource : EntitlementSource {

    override fun observe(): Flow<Entitlements> = flowOf(FREE)

    /** Nothing to ask. There is no store to ask it of yet. */
    override suspend fun refresh() = Unit

    private companion object {
        val FREE = Entitlements(Plan.FREE)
    }
}
