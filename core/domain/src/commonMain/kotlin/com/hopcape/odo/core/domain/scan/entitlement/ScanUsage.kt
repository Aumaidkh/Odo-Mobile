package com.hopcape.odo.core.domain.scan.entitlement

/**
 * How many scans the owner has spent in the period the cap applies to.
 *
 * Separate from [ScanAllowance], which says how many the plan permits. One is the plan and
 * the other is the tally, they come from different places, and the screen needs both to say
 * "2 of 3 free".
 *
 * Which period "this" month is, is the implementation's to know — the caller has no business
 * deciding when a cap resets, and every caller deciding separately is how two screens end up
 * disagreeing about it.
 */
interface ScanUsage {

    /** Scans spent in the current period. Zero at the start of one. */
    suspend fun usedThisMonth(): Int

    /**
     * Count one scan against the current period.
     *
     * Called once a scan has produced something the owner can use. A read that failed or came
     * back empty is not charged: it costs nothing to run and gave them nothing, and charging
     * for it would make a blurry photo cost a scan.
     */
    suspend fun recordScan()
}
