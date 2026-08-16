package com.hopcape.odo.core.domain.scan.entitlement

/**
 * How many scans the owner has spent against the cap.
 *
 * Separate from [ScanAllowance], which says how many the plan permits. One is the plan and
 * the other is the tally, they come from different places, and the screen needs both to say
 * "2 of 5 free".
 *
 * The count is a lifetime one (#248). It was per calendar month, which meant the cap never
 * bound — an owner services a car three or four times a *year*. Which period the cap applies
 * to is the implementation's to know: the caller has no business deciding when a cap resets,
 * and every caller deciding separately is how two screens end up disagreeing about it.
 */
interface ScanUsage {

    /** Scans spent so far. Zero before the first one. */
    suspend fun used(): Int

    /**
     * Count one scan against the current period.
     *
     * Called once a scan has produced something the owner can use. A read that failed or came
     * back empty is not charged: it costs nothing to run and gave them nothing, and charging
     * for it would make a blurry photo cost a scan.
     */
    suspend fun recordScan()
}
