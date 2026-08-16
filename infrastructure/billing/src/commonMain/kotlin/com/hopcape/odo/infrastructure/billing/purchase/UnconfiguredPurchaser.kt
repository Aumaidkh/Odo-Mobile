package com.hopcape.odo.infrastructure.billing.purchase

import arrow.core.Either
import arrow.core.left
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.subscription.RestoreOutcome
import com.hopcape.odo.core.domain.subscription.SubscriptionPurchaser

/**
 * The purchaser a build with no RevenueCat key gets: nothing can be bought.
 *
 * Bound rather than omitted, for the same reason as `UnconfiguredCatalog`: the paywall's
 * ViewModel asks for a purchaser the moment it is built, and a missing Koin definition is a
 * crash rather than an empty state.
 *
 * In practice neither method is reachable — the catalog on such a build answers
 * [DomainError.NothingForSale], so the paywall never draws a plan card or a CTA to tap. They
 * answer honestly anyway, because "unreachable" is a claim about today's screens.
 */
internal class UnconfiguredPurchaser : SubscriptionPurchaser {

    override suspend fun purchase(planId: String): Either<DomainError, Unit> =
        DomainError.NothingForSale.left()

    override suspend fun restore(): Either<DomainError, RestoreOutcome> =
        DomainError.StoreUnavailable.left()
}
