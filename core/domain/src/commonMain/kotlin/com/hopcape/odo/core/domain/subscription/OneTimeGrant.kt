package com.hopcape.odo.core.domain.subscription

/**
 * What a completed one-time purchase entitles the owner to.
 *
 * The mapping from a product to a balance, stated once. It was in the paywall's own offer
 * list, which was fine while the paywall was the only thing that granted anything — it is
 * not, now that a purchase completed while the app was closed has to be claimed on the next
 * launch. Two copies of "a pack is three checks" is a refund waiting to be wrong.
 */
enum class OneTimeGrant(
    val productId: String,
    /** Bill checks this awards. */
    val scanChecks: Int = 0,
    /** Record PDF exports this awards. */
    val recordExports: Int = 0,
) {
    BILL_CHECK_SINGLE(OneTimeProducts.BILL_CHECK_SINGLE, scanChecks = 1),
    BILL_CHECK_PACK(OneTimeProducts.BILL_CHECK_PACK, scanChecks = 3),
    RECORD_EXPORT(OneTimeProducts.RECORD_EXPORT, recordExports = 1),
    ;

    companion object {
        /**
         * What [productId] grants, or null for a product this build does not know.
         *
         * Null is reachable: the store can report a purchase of something a later release
         * added, or something removed from this one. Awarding nothing is the safe answer —
         * the transaction stays unclaimed and a build that understands it can honour it.
         */
        fun of(productId: String): OneTimeGrant? = entries.firstOrNull { it.productId == productId }
    }
}

/**
 * Awards what a purchase bought.
 *
 * A port rather than the callers reaching for each balance, because there are two of them —
 * the sheet that sells, and the reconciler that catches up on a purchase completed while the
 * app was closed — and both must credit the same thing the same way.
 */
fun interface OneTimeGrants {

    /** Credit everything [grant] entitles the owner to. */
    suspend fun award(grant: OneTimeGrant)
}

/** A consumable the store says was paid for. */
data class CompletedPurchase(
    /**
     * The store's own id for this transaction.
     *
     * What makes claiming it exactly once possible. Two purchases of the same pack are two
     * transactions, and the product id alone could not tell them apart.
     */
    val transactionId: String,
    val productId: String,
)

/**
 * Which purchases this device has already credited.
 *
 * A purchase can be reported to the app more than once — every launch, every customer-info
 * refresh — and crediting it twice hands out checks nobody paid for.
 */
interface PurchaseLedger {

    /**
     * Record [transactionId] as credited, answering whether this call is the one that did it.
     *
     * True exactly once per id, however many callers race. That is the whole contract: the
     * caller awards only when it gets true, so the award and the record cannot disagree.
     */
    suspend fun claim(transactionId: String): Boolean
}

/**
 * Credits any purchase the store has recorded and this device has not.
 *
 * The gap it closes: a purchase that completes while the app is closed — the store sheet
 * finishing after the app is killed, a card that needed a bank approval, a restore on a new
 * phone — takes the owner's money and grants nothing, because the only thing that credited a
 * balance was the screen that started the purchase.
 */
fun interface PurchaseReconciler {

    /** Claim everything outstanding. Safe to call as often as anything likes. */
    suspend fun claimOutstanding()
}
