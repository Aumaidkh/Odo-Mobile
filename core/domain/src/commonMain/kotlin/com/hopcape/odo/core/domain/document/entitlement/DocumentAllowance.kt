package com.hopcape.odo.core.domain.document.entitlement

/**
 * Port answering how many documents the owner's plan lets them keep.
 *
 * The free tier caps the vault at three documents (PRD §pricing). That cap is deliberately
 * **not** a database constraint — pricing experiments must not need a migration — so it has
 * to be enforced in the application layer, and this is the seam it is enforced through.
 *
 * `suspend` because a real answer is read, not known: entitlements come from the owner's
 * subscription state, which lives in the local DB and is refreshed from the server. Today's
 * adapter answers "free tier" for everyone (nothing sells a subscription yet); when payments
 * land it implements this same port and the binding swaps, with no caller touched.
 */
fun interface DocumentAllowance {

    /** The limit in force for the current owner right now. */
    suspend fun current(): DocumentLimit
}

/** How many documents a plan permits. */
sealed interface DocumentLimit {

    /** A capped plan — [max] live documents. */
    data class UpTo(val max: Int) : DocumentLimit

    /** A paid plan with no cap. */
    data object Unlimited : DocumentLimit

    /**
     * Whether one more document fits, given how many the owner already holds.
     *
     * Takes the count rather than comparing inside the adapter so the rule is stated once,
     * here, where it can be read next to the limit it applies to.
     */
    fun allows(currentCount: Int): Boolean = when (this) {
        Unlimited -> true
        is UpTo -> currentCount < max
    }

    /** The cap as a number for messaging ("3 of 3 used"), or `null` when there isn't one. */
    val cap: Int? get() = (this as? UpTo)?.max
}
