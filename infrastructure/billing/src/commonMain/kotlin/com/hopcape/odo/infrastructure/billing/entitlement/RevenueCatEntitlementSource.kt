package com.hopcape.odo.infrastructure.billing.entitlement

import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.Entitlements
import com.hopcape.odo.core.domain.entitlement.Plan
import com.hopcape.odo.infrastructure.billing.CustomerInfoStream
import com.hopcape.odo.infrastructure.billing.observability.BillingTelemetry
import com.revenuecat.purchases.kmp.models.CustomerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * [EntitlementSource] over RevenueCat's `CustomerInfo`.
 *
 * The plan is whatever the [PRO_ENTITLEMENT] entitlement says, and nothing here decides it.
 * RevenueCat validates the store receipt on its own servers, so what arrives is a verdict,
 * not a claim the client could have made up.
 *
 * The customer info itself comes from [CustomerInfoStream], which owns the SDK's single
 * delegate. See its note on why the first value is withheld until the store has answered.
 */
internal class RevenueCatEntitlementSource(
    private val stream: CustomerInfoStream,
    private val telemetry: BillingTelemetry,
) : EntitlementSource {

    override fun observe(): Flow<Entitlements> = stream.resolved
        .map { info -> Entitlements(info?.toPlan() ?: Plan.FREE) }
        .onEach { telemetry.entitlementRead(it.plan.name) }

    override suspend fun refresh() = stream.refresh()

    /**
     * Pro while the entitlement is active.
     *
     * `isActive` stays true through a grace period — Play keeps an entitlement live while it
     * retries a card that failed — which is what the app wants: taking the vault away while
     * someone fixes their payment method would punish them for their bank's timing. It goes
     * false on account hold, when the store has genuinely given up.
     *
     * A read that failed arrives here as null and reads as free. An entitlement the app
     * cannot prove must not unlock Pro; the next successful read puts it back, and a purchase
     * pushes one through the delegate immediately.
     */
    private fun CustomerInfo.toPlan(): Plan =
        if (entitlements[PRO_ENTITLEMENT]?.isActive == true) Plan.PRO else Plan.FREE

    internal companion object {

        /**
         * The entitlement identifier in the RevenueCat dashboard.
         *
         * Both plans grant this one entitlement, which is why nothing downstream cares which
         * plan was bought — `ProFeature` asks what the owner may do, not what they paid.
         */
        const val PRO_ENTITLEMENT = "pro"
    }
}
