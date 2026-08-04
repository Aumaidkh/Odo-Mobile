package com.hopcape.odo.core.domain.scan.entitlement

/**
 * Port answering how many AI scans the owner's plan still permits this month.
 *
 * The free tier gets three (PRD §pricing). Like the document cap, the number is enforced in
 * the application layer rather than as a constraint, so pricing can change without a
 * migration.
 *
 * **The client only ever mirrors this.** The real limit is enforced inside the Edge Function
 * against `ai_usage` before it calls Anthropic (TDD §7.5), because a client-side count is a
 * number an attacker edits. What this port is for is telling the owner where they stand
 * *before* they take a photo — a quota refused after the shutter is a worse experience than
 * one shown in the top bar all along.
 */
fun interface ScanAllowance {

    /** The limit in force for the current owner right now. */
    suspend fun current(): ScanLimit
}

/** How many AI scans a plan permits in the current period, and how many are left. */
sealed interface ScanLimit {

    /** A capped plan: [used] of [max] scans spent this period. */
    data class UpTo(val max: Int, val used: Int) : ScanLimit

    /** A paid plan with no cap. */
    data object Unlimited : ScanLimit

    /** Whether one more scan fits. */
    val allowsAnother: Boolean
        get() = when (this) {
            Unlimited -> true
            is UpTo -> used < max
        }

    /** How many are left, or `null` when there is no cap to count down from. */
    val remaining: Int?
        get() = when (this) {
            Unlimited -> null
            is UpTo -> (max - used).coerceAtLeast(0)
        }

    /** The cap as a number for the "2 of 3 free" pill, or `null` when there isn't one. */
    val cap: Int? get() = (this as? UpTo)?.max
}
