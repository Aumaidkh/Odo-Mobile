package com.hopcape.odo.infrastructure.billing.purchase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.subscription.RestoreOutcome
import com.hopcape.odo.core.domain.subscription.SubscriptionPurchaser
import com.hopcape.odo.infrastructure.billing.entitlement.RevenueCatEntitlementSource.Companion.PRO_ENTITLEMENT
import com.hopcape.odo.infrastructure.billing.observability.BillingTelemetry
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.either.awaitOfferingsEither
import com.revenuecat.purchases.kmp.either.awaitPurchaseEither
import com.revenuecat.purchases.kmp.either.awaitRestoreEither
import com.revenuecat.purchases.kmp.models.Package

/**
 * [SubscriptionPurchaser] over RevenueCat.
 *
 * Buying needs the store's own `Package` object, not an identifier, so the offering is read
 * again here and the plan found in it. That re-read is deliberate rather than a cache the
 * paywall passes down: the sheet the owner is about to see is the store's, and it must be
 * built from what the store says right now — not from an offering fetched before they went
 * to make tea.
 *
 * Nothing here reports the new entitlement. The purchase pushes a `CustomerInfo` update
 * through `RevenueCatEntitlementSource`'s delegate, so every gated screen has already changed
 * by the time this returns.
 */
internal class RevenueCatPurchaser(
    private val telemetry: BillingTelemetry,
) : SubscriptionPurchaser {

    override suspend fun purchase(planId: String): Either<DomainError, Unit> {
        val plan = findPackage(planId) ?: return DomainError.NothingForSale.left()

        telemetry.purchaseStarted(planId)
        return Purchases.sharedInstance.awaitPurchaseEither(packageToPurchase = plan).fold(
            ifLeft = { failure ->
                if (failure.userCancelled) {
                    // The most common ending a paywall has. Not a failure, and never an error
                    // message: they looked at the price and said no.
                    telemetry.purchaseCancelled(planId)
                    DomainError.PaymentCancelled.left()
                } else {
                    telemetry.purchaseFailed(planId, failure.error.code.toString(), failure.error.message)
                    DomainError.PaymentFailed.left()
                }
            },
            ifRight = {
                telemetry.purchaseCompleted(planId)
                Unit.right()
            },
        )
    }

    override suspend fun restore(): Either<DomainError, RestoreOutcome> =
        Purchases.sharedInstance.awaitRestoreEither().fold(
            ifLeft = { error ->
                telemetry.restoreFailed(error.code.toString(), error.message)
                DomainError.StoreUnavailable.left()
            },
            ifRight = { info ->
                val restored = info.entitlements[PRO_ENTITLEMENT]?.isActive == true
                telemetry.restored(restored)
                if (restored) RestoreOutcome.ProRestored.right() else RestoreOutcome.NothingToRestore.right()
            },
        )

    /** The package the paywall showed, found again in the offering it came from. */
    private suspend fun findPackage(planId: String): Package? =
        Purchases.sharedInstance.awaitOfferingsEither().getOrNull()
            ?.current
            ?.availablePackages
            ?.firstOrNull { it.identifier == planId }
}
