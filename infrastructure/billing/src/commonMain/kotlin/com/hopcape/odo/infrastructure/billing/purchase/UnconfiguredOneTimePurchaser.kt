package com.hopcape.odo.infrastructure.billing.purchase

import arrow.core.Either
import arrow.core.left
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.subscription.OneTimePurchaser

/**
 * The one-time purchaser a build with no RevenueCat key gets: nothing can be bought, and
 * [priceOf] answers null so the share sheet offers only the Pro route rather than a button
 * with no price on it.
 *
 * Bound rather than omitted, for the same reason as [UnconfiguredPurchaser]: a missing Koin
 * definition is a crash on a screen that has nothing to do with billing.
 */
internal class UnconfiguredOneTimePurchaser : OneTimePurchaser {

    override suspend fun purchase(productId: String): Either<DomainError, Unit> =
        DomainError.NothingForSale.left()

    override suspend fun priceOf(productId: String): String? = null
}
