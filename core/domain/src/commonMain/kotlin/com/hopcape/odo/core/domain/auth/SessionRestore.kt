package com.hopcape.odo.core.domain.auth

/**
 * Loads the stored session back into memory at startup.
 *
 * A separate port because the thing that owns sessions lives in `:feature:auth` and is
 * internal to it, while the only caller is the app's startup path. The three read-only
 * session ports next to this one ([AccessTokenProvider],
 * [com.hopcape.odo.core.domain.owner.SessionStatusProvider],
 * [com.hopcape.odo.core.domain.owner.CurrentOwnerProvider]) answer questions; this one is
 * the single write the startup path is allowed to make.
 *
 * **Nothing works without this call.** Tokens are written to secure storage at sign-in but
 * held in memory for the rest of the process, so a launch that never restores looks exactly
 * like a launch by someone who never signed in: the sync gate sees no token and skips every
 * run, and Profile offers to sign in again. The data is all still there, and none of it
 * reaches the server.
 */
fun interface SessionRestore {

    /**
     * Read the stored session, if there is one.
     *
     * Restoring is not validating: a session read from disk may already be past its expiry.
     * The first caller that needs a live token refreshes it.
     */
    suspend fun restore()
}
