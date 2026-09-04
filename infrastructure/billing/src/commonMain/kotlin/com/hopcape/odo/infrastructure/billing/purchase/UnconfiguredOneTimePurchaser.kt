package com.hopcape.odo.infrastructure.billing.purchase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.subscription.CompletedPurchase
import com.hopcape.odo.core.domain.subscription.OneTimePurchaser

/**
 * The one-time purchaser a build with no RevenueCat key gets: nothing can be bought, and the
 * store prices nothing, so the share sheet offers only the Pro route rather than a button
 * with no price on it.
 *
 * Bound rather than omitted, for the same reason as [UnconfiguredPurchaser]: a missing Koin
 * definition is a crash on a screen that has nothing to do with billing.
 */
internal class UnconfiguredOneTimePurchaser : OneTimePurchaser {

    override suspend fun purchase(productId: String): Either<DomainError, Unit> =
        DomainError.NothingForSale.left()

    /**
     * An empty catalogue rather than a failure. A build with no key genuinely has nothing for
     * sale; saying the store could not be reached would put a retry in front of someone whose
     * retry can never work.
     */
    override suspend fun pricesOf(productIds: List<String>): Either<DomainError, Map<String, String>> =
        emptyMap<String, String>().right()

    /** No store, so nothing was ever bought — and nothing to claim on the next launch. */
    override suspend fun completedPurchases(): Either<DomainError, List<CompletedPurchase>> =
        emptyList<CompletedPurchase>().right()
}
