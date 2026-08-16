package com.hopcape.odo.core.domain.subscription

import arrow.core.Either
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Port over buying something once rather than subscribing to it (#246).
 *
 * Separate from [SubscriptionPurchaser] because the two are different products with
 * different lifetimes, not two methods of one idea. A subscription renews, lapses, enters
 * grace and is restored; a consumable is bought, spent, and bought again — and restoring
 * one would be wrong, since it was consumed on purpose.
 *
 * What a completed purchase grants is not returned here. The balance lives in
 * [ExportCredits][com.hopcape.odo.core.domain.record.entitlement.ExportCredits] and the
 * caller credits it, for the same reason [SubscriptionPurchaser] does not return
 * entitlement: two answers to one question diverge the first time a purchase completes
 * somewhere else.
 */
interface OneTimePurchaser {

    /**
     * Take the owner through the store's purchase sheet for [productId].
     *
     * Backing out is [DomainError.PaymentCancelled], not a failure — the most common ending
     * a purchase sheet has, and it must not put an error in front of someone who changed
     * their mind.
     */
    suspend fun purchase(productId: String): Either<DomainError, Unit>

    /** The store's own formatted price for [productId], or null when it has no such product. */
    suspend fun priceOf(productId: String): String?
}

/** The one consumable Odo sells: a single record PDF export (#246). */
object OneTimeProducts {
    const val RECORD_EXPORT = "odo_record_export"
}
