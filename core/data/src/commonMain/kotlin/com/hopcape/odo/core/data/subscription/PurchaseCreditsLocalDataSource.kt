package com.hopcape.odo.core.data.subscription

import com.hopcape.odo.core.domain.subscription.CreditKind

/**
 * Storage for what the owner bought one at a time, and what they have spent of it.
 *
 * One interface for both halves because they are one balance: [available] is what [claim]
 * granted minus what [spend] took, and splitting them across two storage ports would let a
 * caller read one without the other.
 */
interface PurchaseCreditsLocalDataSource {

    /**
     * Record [transactionId] as honoured, worth [scanChecks] and [recordExports].
     *
     * True only for the call that recorded it, so a caller never has to read then write and
     * race a second pass into a double credit.
     */
    suspend fun claim(transactionId: String, scanChecks: Int, recordExports: Int): Boolean

    /** Granted minus spent, for [kind]. Never negative. */
    suspend fun available(kind: CreditKind): Int

    /** Take one of [kind] if there is one, saying whether it took it. */
    suspend fun spend(kind: CreditKind): Boolean
}
