package com.hopcape.odo.infrastructure.billing

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The one `CustomerInfo` this app has, and the one `PurchasesDelegate` it is allowed.
 *
 * RevenueCat permits a single delegate, so it cannot be claimed by whichever adapter happens
 * to need customer info — the second one to set it would silently unsubscribe the first. This
 * object owns it, and both the entitlement source and the subscription status read from here.
 *
 * **Every read is guarded by [Purchases.isConfigured].** A build with a key can still reach a
 * device where `configure()` failed, and `Purchases.sharedInstance` throws there rather than
 * returning null. Unconfigured resolves to `null`, which reads as "no subscription".
 *
 * **[resolved] withholds its first value until the store has answered.** Emitting "no
 * subscription" first and correcting it a moment later would tell a paying owner they are on
 * the free plan for as long as the read takes — long enough for the vault to refuse their
 * fourth document. A read that fails resolves to `null` instead, so a collector waits for an
 * answer and never forever.
 */
internal class CustomerInfoStream(
    scope: CoroutineScope,
    private val telemetry: BillingTelemetry,
) : PurchasesDelegate {

    /** Null while nothing has been read yet; a box holding null once a read failed. */
    private val state = MutableStateFlow<Answer?>(null)

    init {
        // Guarded, because `Purchases.sharedInstance` throws when configure() never ran.
        // The module picks this branch on a build-time key, and RevenueCatBootstrap swallows
        // a configure() that fails at run time — so a key can be present and the SDK still
        // not be there. This object is now built during startup, where that throw would be a
        // launch crash rather than a screen that fails.
        if (Purchases.isConfigured) {
            Purchases.sharedInstance.delegate = this
            scope.launch { refresh() }
        } else {
            // Answered, not left waiting: `resolved` withholds its first value, so a
            // collector with nothing coming would hold the free plan open forever.
            state.value = Answer(null)
        }
    }

    /** What the store knows about this owner, or null when it could not be asked. */
    val resolved: Flow<CustomerInfo?> = state.filterNotNull().map { it.info }

    /** Ask again. Used when the app comes back to the foreground, or on a pull to refresh. */
    suspend fun refresh() {
        if (!Purchases.isConfigured) {
            state.value = Answer(null)
            return
        }
        Purchases.sharedInstance.awaitCustomerInfoEither().fold(
            ifLeft = { error ->
                telemetry.entitlementFailed(error.code.toString(), error.message)
                state.value = Answer(null)
            },
            ifRight = { info -> state.value = Answer(info) },
        )
    }

    /**
     * The store pushed an update — a purchase completed, a renewal went through, a
     * subscription lapsed. This is what makes a paywall purchase unlock the screen behind it
     * without anything having to reopen.
     */
    override fun onCustomerInfoUpdated(customerInfo: CustomerInfo) {
        state.value = Answer(customerInfo)
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

    /** A box, so "not asked yet" and "asked and got nothing" are different states. */
    private data class Answer(val info: CustomerInfo?)
}
