package com.hopcape.odo.core.data.record

/**
 * Storage for the bought-and-unspent record exports (#246).
 *
 * [spend] both takes the credit and says whether there was one, so a caller never has to
 * read then write and race itself against a second share.
 */
interface ExportCreditsLocalDataSource {

    /** Credits left. Zero for anyone who has never bought one. */
    suspend fun remaining(): Int

    /** Record a completed purchase. */
    suspend fun grant()

    /** Take one if there is one; false when the balance was already empty. */
    suspend fun spend(): Boolean
}
