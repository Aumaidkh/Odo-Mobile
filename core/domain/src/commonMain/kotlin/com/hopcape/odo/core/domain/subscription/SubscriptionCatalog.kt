package com.hopcape.odo.core.domain.subscription

import arrow.core.Either
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Port over what the store currently sells.
 *
 * Read every time the paywall opens rather than cached. Prices, trial eligibility and which
 * plans exist are all the store's to change, and a cached offer is a price the app is no
 * longer sure of. The paywall shows a skeleton while this answers.
 *
 * Fails loudly rather than falling back. There is no offline copy of the offer and there
 * deliberately is not one: `DomainError.StoreUnavailable` puts a retry on screen, which is
 * honest, while a remembered price risks charging someone a figure they were not shown.
 */
fun interface SubscriptionCatalog {

    /** What is for sale now, or why it could not be found out. */
    suspend fun current(): Either<DomainError, Offer>
}
