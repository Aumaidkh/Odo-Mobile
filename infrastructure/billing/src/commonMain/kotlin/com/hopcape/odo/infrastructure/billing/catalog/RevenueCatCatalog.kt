package com.hopcape.odo.infrastructure.billing.catalog

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.subscription.Offer
import com.hopcape.odo.core.domain.subscription.SubscriptionCatalog
import com.hopcape.odo.infrastructure.billing.observability.BillingTelemetry
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.either.awaitOfferingsEither

/**
 * [SubscriptionCatalog] over RevenueCat's current offering.
 *
 * Asks the SDK every time. RevenueCat caches offerings itself and answers from that cache
 * when it can, so the repeated call is cheap — and going through it is what keeps a price
 * change in Play Console reaching the app without a release.
 *
 * The two failures are kept apart because only one of them is worth a retry button. A
 * request that did not come back is [DomainError.StoreUnavailable]; an offering that came
 * back with nothing this app can show is [DomainError.NothingForSale], which no amount of
 * retrying fixes because it is a dashboard misconfiguration.
 */
internal class RevenueCatCatalog(
    private val telemetry: BillingTelemetry,
) : SubscriptionCatalog {

    override suspend fun current(): Either<DomainError, Offer> =
        Purchases.sharedInstance.awaitOfferingsEither().fold(
            ifLeft = { error ->
                telemetry.offeringsFailed(error.code.toString(), error.message)
                DomainError.StoreUnavailable.left()
            },
            ifRight = { offerings ->
                val current = offerings.current
                if (current == null) {
                    telemetry.noCurrentOffering()
                    return DomainError.NothingForSale.left()
                }
                val offer = OfferMapper.map(current, onDropped = telemetry::packageDropped)
                if (offer == null) {
                    telemetry.noUsablePackages(current.identifier)
                    DomainError.NothingForSale.left()
                } else {
                    telemetry.offeringsLoaded(offer.id, offer.plans.size)
                    offer.right()
                }
            },
        )
}
