package com.hopcape.odo.core.domain.scan.entitlement

/**
 * Charges one scan to whatever can pay for it.
 *
 * One port rather than each caller deciding, because there are two of them — a bill and a
 * document — and "free first, bought after" is the kind of rule that survives being written
 * once and drifts the moment it is written twice.
 *
 * The order is not arbitrary: spending a bought check while a free one is still available
 * would sell the owner something they already had.
 */
fun interface ScanCharger {

    /**
     * Count one scan.
     *
     * Called once a scan has produced something the owner can use. A read that failed or
     * came back empty is not charged: it costs nothing to run and gave them nothing.
     */
    suspend fun chargeOne()
}
