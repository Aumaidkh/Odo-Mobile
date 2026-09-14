package com.hopcape.odo.core.domain.scan.entitlement

/**
 * How many bill checks the owner may still run.
 *
 * **Separate from [ScanAllowance], and that separation is the point.** The two used to be one
 * balance, so five scanned bills closed the check behind a wall the owner had never spent
 * anything on — they had photographed bills, which is the thing the app most wants them to
 * do, and it cost them the feature that makes photographing worthwhile.
 *
 * Reads [ScanLimit] because the shape is the same: a cap, a tally, and whatever was bought.
 */
fun interface CheckAllowance {

    suspend fun current(): ScanLimit
}

/**
 * How many checks the owner has spent against the cap.
 *
 * A lifetime tally like the scan one, and device-local for the same reason: the cap is a
 * commercial limit on something that costs nothing to run, and counting it server-side would
 * need an account the app does not require.
 */
interface CheckUsage {

    /** Checks spent so far. Zero before the first one. */
    suspend fun used(): Int

    /**
     * Count one check.
     *
     * Called once a check has said something the owner can use. A read that named no line is
     * not charged — it gave them nothing, and charging for it would make an unreadable bill
     * cost a check.
     */
    suspend fun recordCheck()
}

/**
 * Spends one check: a free one while any remain, then a bought one.
 *
 * The order is the whole rule, the same as the scanner's: taking a bought check while a free
 * one is still there sells the owner something they already had, and they find out by running
 * out early.
 */
fun interface CheckCharger {

    suspend fun chargeOne()
}
