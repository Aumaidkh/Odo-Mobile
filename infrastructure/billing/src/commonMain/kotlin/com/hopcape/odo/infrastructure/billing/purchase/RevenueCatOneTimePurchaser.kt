package com.hopcape.odo.infrastructure.billing.purchase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.subscription.OneTimePurchaser
import com.hopcape.odo.infrastructure.billing.observability.BillingTelemetry
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.either.awaitGetProductsEither
import com.revenuecat.purchases.kmp.either.awaitPurchaseEither
import com.revenuecat.purchases.kmp.models.StoreProduct

/**
 * [OneTimePurchaser] over RevenueCat (#246).
 *
 * Products are fetched by identifier rather than read out of an offering: a consumable is
 * not a plan and does not belong on the paywall's plan list, so there is no package to find.
 * The fetch happens per call for the same reason [RevenueCatPurchaser] re-reads its offering
 * — the sheet the owner is about to see is the store's, and it must be built from what the
 * store says right now.
 *
 * Nothing here credits the balance. The caller does, once this returns without an error, so
 * the store's answer and the balance cannot disagree about whether a purchase happened.
 */
internal class RevenueCatOneTimePurchaser(
    private val telemetry: BillingTelemetry,
) : OneTimePurchaser {

    override suspend fun purchase(productId: String): Either<DomainError, Unit> {
        val product = findProduct(productId) ?: return DomainError.NothingForSale.left()

        telemetry.purchaseStarted(productId)
        return Purchases.sharedInstance.awaitPurchaseEither(storeProduct = product).fold(
            ifLeft = { failure ->
                if (failure.userCancelled) {
                    telemetry.purchaseCancelled(productId)
                    DomainError.PaymentCancelled.left()
                } else {
                    telemetry.purchaseFailed(productId, failure.error.code.toString(), failure.error.message)
                    DomainError.PaymentFailed.left()
                }
            },
            ifRight = {
                telemetry.purchaseCompleted(productId)
                Unit.right()
            },
        )
    }

    /**
     * One call for the lot, and a `Left` when the store itself could not be reached.
     *
     * The failure has to survive: `getOrNull()` here would turn an offline store into an
     * empty catalogue, and the caller would tell the owner nothing is for sale.
     */
    override suspend fun pricesOf(productIds: List<String>): Either<DomainError, Map<String, String>> =
        Purchases.sharedInstance.awaitGetProductsEither(productIds).fold(
            ifLeft = { failure ->
                telemetry.offeringsFailed(failure.code.toString(), failure.message)
                DomainError.StoreUnavailable.left()
            },
            ifRight = { products ->
                products.associate { it.id to it.price.formatted }.right()
            },
        )

    private suspend fun findProduct(productId: String): StoreProduct? =
        Purchases.sharedInstance.awaitGetProductsEither(listOf(productId)).getOrNull()
            ?.firstOrNull { it.id == productId }
}
