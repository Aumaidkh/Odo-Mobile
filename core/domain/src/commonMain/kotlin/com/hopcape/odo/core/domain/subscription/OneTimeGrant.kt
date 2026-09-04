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

/** Which balance a credit belongs to. */
enum class CreditKind { BILL_CHECK, RECORD_EXPORT }

/**
 * Honours a purchase, exactly once per owner.
 *
 * One port rather than a ledger and a separate grant, because they are one write: the record
 * that a transaction was honoured **is** what the owner was given. Two writes could disagree,
 * and the way they would disagree is a purchase marked honoured that credited nothing.
 *
 * Exactly once **per owner**, not per install. The store reports a completed purchase
 * forever, so a record that a reinstall clears lets the same purchase be honoured again on
 * every install.
 */
fun interface PurchaseGrants {

    /**
     * Honour [transactionId], crediting what [grant] is worth.
     *
     * True only for the call that honoured it — false when this owner already had. Callers
     * that spend what they just bought rely on that: a second true would be a second credit.
     */
    suspend fun claim(transactionId: String, grant: OneTimeGrant): Boolean
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
 * Credits any purchase the store has recorded and this owner has not.
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
