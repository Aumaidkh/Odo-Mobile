package com.hopcape.odo.core.domain.scan.entitlement

/**
 * Which bills have already been checked and paid for.
 *
 * The result screen re-reads every time it is opened, so without a record of what has been
 * charged for, a second look at the same bill costs a second check. The owner's money, and
 * — because the same pass files its prices into the shared pool — everyone else's bands.
 */
interface BillCheckLedger {

    /**
     * Record [billId] as checked, answering whether this call is the one that did it.
     *
     * True exactly once per bill, however many reads race. That is the whole contract: the
     * caller charges only when it gets true, so the charge and the record cannot disagree.
     */
    suspend fun claim(billId: String): Boolean

    /**
     * Whether [billId] has already been checked and paid for.
     *
     * A receipt, and the reason a re-read cannot take an answer back: the screen re-reads on
     * every visit, and without this the second read would ask the allowance again and mask a
     * result the owner had already bought.
     */
    suspend fun wasChecked(billId: String): Boolean
}
