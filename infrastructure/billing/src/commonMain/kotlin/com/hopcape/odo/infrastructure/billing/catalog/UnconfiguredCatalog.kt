package com.hopcape.odo.infrastructure.billing.catalog

import arrow.core.Either
import arrow.core.left
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.subscription.Offer
import com.hopcape.odo.core.domain.subscription.SubscriptionCatalog

/**
 * The catalog a build with no RevenueCat key gets: nothing is for sale.
 *
 * Registered rather than omitted, because a missing definition is a crash the moment someone
 * opens the paywall, and "this build cannot sell anything" is a fact the app can state calmly
 * instead. The paywall already has a screen for it.
 *
 * [DomainError.NothingForSale] rather than [DomainError.StoreUnavailable] deliberately:
 * retrying will never help a build that was compiled without a key, so the paywall must not
 * offer a retry button that cannot work.
 */
internal class UnconfiguredCatalog : SubscriptionCatalog {

    override suspend fun current(): Either<DomainError, Offer> = DomainError.NothingForSale.left()
}
