package com.hopcape.odo.infrastructure.billing.entitlement

import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.Entitlements
import com.hopcape.odo.core.domain.entitlement.Plan
import com.hopcape.odo.infrastructure.billing.observability.BillingTelemetry
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesDelegate
import com.revenuecat.purchases.kmp.either.awaitCustomerInfoEither
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.PurchasesError
import com.revenuecat.purchases.kmp.models.StoreProduct
import com.revenuecat.purchases.kmp.models.StoreTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * [EntitlementSource] over RevenueCat's `CustomerInfo`.
 *
 * The plan is whatever the [PRO_ENTITLEMENT] entitlement says, and nothing here decides it.
 * RevenueCat validates the store receipt on its own servers, so what arrives is a verdict,
 * not a claim the client could have made up.
 *
 * **One delegate, set once.** RevenueCat allows a single `PurchasesDelegate`, so this object
 * is it — every consumer shares the one state this holds rather than each collection
 * replacing the delegate and silently unsubscribing the others. Anything else that needs
 * customer-info updates later has to come through here.
 *
 * **`observe()` waits rather than guessing.** The first value is withheld until the store has
 * actually answered, because emitting [Entitlements.Unknown] first would tell a paying owner
 * they are on the free plan for as long as the read takes — long enough for the vault to
 * refuse their fourth document. A read that fails resolves to [Entitlements.Unknown] anyway,
 * so a collector waits for an answer, never forever.
 */
internal class RevenueCatEntitlementSource(
    private val scope: CoroutineScope,
    private val telemetry: BillingTelemetry,
) : EntitlementSource, PurchasesDelegate {

    /** Null until the store has answered once. See the class note on why that matters. */
    private val current = MutableStateFlow<Entitlements?>(null)

    init {
        Purchases.sharedInstance.delegate = this
        scope.launch { refresh() }
    }

    override fun observe(): Flow<Entitlements> = current.filterNotNull()

    override suspend fun refresh() {
        Purchases.sharedInstance.awaitCustomerInfoEither().fold(
            ifLeft = ::resolveUnknown,
            ifRight = { info -> publish(info) },
        )
    }

    /**
     * The store pushed an update — a purchase completed, a renewal went through, a
     * subscription lapsed. This is what makes a paywall purchase unlock the screen behind it
     * without anything having to reopen.
     */
    override fun onCustomerInfoUpdated(customerInfo: CustomerInfo) {
        publish(customerInfo)
    }

    /**
     * An App Store promoted purchase, started from outside the app.
     *
     * Deliberately not started: Odo does not ship on the App Store yet, and silently putting
     * a purchase sheet in front of someone who did not ask for one is worse than declining a
     * path nobody can reach. Revisit with iOS.
     */
    override fun onPurchasePromoProduct(
        storeProduct: StoreProduct,
        startPurchase: (
            onError: (error: PurchasesError, userCancelled: Boolean) -> Unit,
            onSuccess: (storeTransaction: StoreTransaction, customerInfo: CustomerInfo) -> Unit,
        ) -> Unit,
    ) = Unit

    private fun publish(info: CustomerInfo) {
        val plan = info.toPlan()
        current.value = Entitlements(plan)
        telemetry.entitlementRead(plan.name)
    }

    /**
     * The store could not be asked, so nothing is proven and nothing is unlocked.
     *
     * Free rather than keeping the last known plan: an entitlement the app cannot prove must
     * not unlock Pro. The next successful read puts it back, and a purchase pushes one
     * through the delegate immediately.
     */
    private fun resolveUnknown(error: PurchasesError) {
        telemetry.entitlementFailed(error.code.toString(), error.message)
        current.value = Entitlements.Unknown
    }

    /**
     * Pro while the entitlement is active.
     *
     * `isActive` stays true through a grace period — Play keeps an entitlement live while it
     * retries a card that failed — which is what the app wants: taking the vault away while
     * someone fixes their payment method would punish them for their bank's timing. It goes
     * false on account hold, when the store has genuinely given up.
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
