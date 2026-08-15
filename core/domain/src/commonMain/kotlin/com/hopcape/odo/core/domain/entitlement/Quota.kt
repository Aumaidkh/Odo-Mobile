package com.hopcape.odo.core.domain.entitlement

/**
 * How much of a [ProFeature] a plan permits.
 *
 * Three answers cover both kinds of gate. A counted feature is [UpTo] on the free plan and
 * [Unlimited] on Pro. An on/off feature is [None] on the free plan and [Unlimited] on Pro.
 * That is why there is one type rather than a boolean for one kind and a limit for the other:
 * the caller asks the same question and the plan decides what the answer means.
 *
 * How many have been used is not held here. A quota is what the plan says; the count is what
 * the device or the vault says, and it is passed in. Keeping them apart is what lets the same
 * quota answer for a vault that counts rows and a scanner that counts a month.
 */
sealed interface Quota {

    /** Not on this plan at all. */
    data object None : Quota

    /** A capped plan — [max] of them. */
    data class UpTo(val max: Int) : Quota

    /** No cap. */
    data object Unlimited : Quota

    /** Whether the plan grants the feature in any amount. */
    val isGranted: Boolean get() = this != None

    /** Whether one more fits, given how many are already [used]. */
    fun allowsAnother(used: Int): Boolean = when (this) {
        None -> false
        Unlimited -> true
        is UpTo -> used < max
    }

    /** The cap as a number for messaging ("3 of 3 used"), or `null` when there isn't one. */
    val cap: Int? get() = (this as? UpTo)?.max

    /** How many are left, or `null` when there is no cap to count down from. */
    fun remaining(used: Int): Int? = when (this) {
        None -> 0
        Unlimited -> null
        is UpTo -> (max - used).coerceAtLeast(0)
    }
}
