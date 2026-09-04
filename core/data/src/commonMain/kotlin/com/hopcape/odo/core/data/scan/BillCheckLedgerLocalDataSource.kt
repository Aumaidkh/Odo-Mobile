package com.hopcape.odo.core.data.scan

/**
 * Which bills have already been checked and paid for.
 *
 * [claim] both records the bill and says whether this call is the one that recorded it, so a
 * caller never has to read then write and race a second look into a second charge.
 */
interface BillCheckLedgerLocalDataSource {

    /** Record [billId] as checked; true only for the call that actually recorded it. */
    suspend fun claim(billId: String): Boolean

    /** Whether [billId] has already been checked and paid for. */
    suspend fun wasChecked(billId: String): Boolean
}
