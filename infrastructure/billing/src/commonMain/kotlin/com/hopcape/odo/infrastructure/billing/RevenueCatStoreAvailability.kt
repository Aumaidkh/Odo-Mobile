package com.hopcape.odo.infrastructure.billing

import com.hopcape.odo.core.domain.subscription.StoreAvailability
import com.hopcape.odo.core.domain.subscription.StoreReadiness
import com.hopcape.odo.core.platform.app.AppInfo
import com.hopcape.odo.infrastructure.billing.observability.BillingTelemetry
import com.hopcape.odo.core.common.runCatchingCancellable
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.ktx.awaitCanMakePayments

/**
 * Asks the store whether it would sell, before the paywall offers to.
 *
 * Play answers a product query for a copy it will not sell to, then fails only once the flow
 * starts — inside its own activity, past any `catch` of ours. Not starting it is the defence.
 *
 * Both checks fail open: a store API that misbehaves must not close a working paywall.
 */
internal class RevenueCatStoreAvailability(
    private val appInfo: AppInfo,
    private val telemetry: BillingTelemetry,
) : StoreAvailability {

    override suspend fun check(): StoreReadiness {
        if (!appInfo.installedFromStore) return report(StoreReadiness.NOT_FROM_STORE)
        // `canMakePayments` reaches the SDK's application context, which only exists once
        // configure() has run; on a device where it failed, sharedInstance throws.
        if (!Purchases.isConfigured) return report(StoreReadiness.PAYMENTS_UNAVAILABLE)

        val canPay = runCatchingCancellable { Purchases.awaitCanMakePayments() }.getOrDefault(true)
        return if (canPay) StoreReadiness.READY else report(StoreReadiness.PAYMENTS_UNAVAILABLE)
    }

    private fun report(readiness: StoreReadiness): StoreReadiness {
        telemetry.storeUnavailable(readiness.name)
        return readiness
    }
}

/** A build with no RevenueCat key sells nothing, so there is nothing to check. */
internal class UnconfiguredStoreAvailability : StoreAvailability {
    override suspend fun check(): StoreReadiness = StoreReadiness.PAYMENTS_UNAVAILABLE
}
