package com.hopcape.odo.core.data.subscription

/**
 * Storage for the one-time purchases this device has credited.
 *
 * [claim] both records the id and says whether this call is the one that recorded it, so a
 * caller never has to read then write and race a second reconcile into a double grant.
 */
interface PurchaseLedgerLocalDataSource {

    /** Record [transactionId]; true only for the call that actually inserted it. */
    suspend fun claim(transactionId: String): Boolean
}
