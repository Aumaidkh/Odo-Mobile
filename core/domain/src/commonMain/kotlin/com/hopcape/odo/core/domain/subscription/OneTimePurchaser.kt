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

    /**
     * The store's own formatted prices for [productIds], keyed by id.
     *
     * A `Left` means the store could not be asked at all. An id **absent from the map** means
     * the store was asked and has no such product. Keeping those apart is the whole reason
     * this exists: a caller listing several products otherwise cannot tell a short catalogue
     * from a failed read, and would show two of three as though the third were never for
     * sale.
     *
     * One call rather than one per id, because the store's API takes a list and three serial
     * round trips is three chances for exactly that partial answer.
     */
    suspend fun pricesOf(productIds: List<String>): Either<DomainError, Map<String, String>>

    /**
     * The store's own formatted price for [productId], or null.
     *
     * Lossy on purpose, and only safe where the caller has nothing to say about the
     * difference: null is both "no such product" and "could not ask". A screen that lists
     * products wants [pricesOf].
     */
    suspend fun priceOf(productId: String): String? =
        pricesOf(listOf(productId)).getOrNull()?.get(productId)
}

/**
 * The consumables Odo sells, for someone who wants one thing rather than a plan.
 *
 * Ids only. What each one grants is the buyer's business, not this object's — the export
 * credits its balance in `ExportCredits`, and the scan packs have no balance to credit yet,
 * which is why nothing offers them for sale.
 */
object OneTimeProducts {

    /** A single record PDF export (#246). */
    const val RECORD_EXPORT = "odo_record_export"

    /** One bill check. */
    const val BILL_CHECK_SINGLE = "odo_bill_check_1"

    /** Three bill checks. */
    const val BILL_CHECK_PACK = "odo_bill_check_3"
}
