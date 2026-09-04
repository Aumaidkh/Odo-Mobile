package com.hopcape.odo.core.data.scan

/**
 * Storage for the bought-and-unspent bill checks.
 *
 * [spend] both takes the credit and says whether there was one, so a caller never has to
 * read then write and race itself against a second scan finishing at the same moment.
 */
interface ScanCreditsLocalDataSource {

    /** Credits left. Zero for anyone who has never bought a pack. */
    suspend fun remaining(): Int

    /** Record a completed purchase of [count] checks. */
    suspend fun grant(count: Int)

    /** Take one if there is one; false when the balance was already empty. */
    suspend fun spend(): Boolean
}
