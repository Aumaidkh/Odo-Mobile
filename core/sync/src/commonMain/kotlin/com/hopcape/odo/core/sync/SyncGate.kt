package com.hopcape.odo.core.sync

/**
 * Whether a sync run is allowed to happen at all right now, and — when it is not — whether
 * asking again later could change the answer.
 *
 * Today this answers "is someone signed in": `owner_id` is stamped server-side from
 * `auth.uid()`, so an anonymous run cannot write anything and would only collect 401s
 * (SYNC_DESIGN §9).
 *
 * **The verdict is three-valued rather than a boolean, and that distinction is the whole
 * point.** "Nobody is signed in" and "we could not read a session just now" both used to
 * come back as `false`, and a `false` gate makes the run `Skipped`, which the worker records
 * as done. For the first case that is right — signing in is what changes it, and burning
 * wakeups until then helps nobody. For the second it lost the run: the sign-in sync asked
 * for a token a moment too early, got null, and WorkManager dropped the job with the initial
 * pull still undone (issue #312).
 *
 * A one-method interface declared here rather than `SessionStatusProvider` from
 * `:core:domain`, because `:core:sync` deliberately depends on nothing — the engine is
 * handed its answers, it does not go looking for them. The binding that connects the two
 * lives in the composition root.
 *
 * The scheduler checks this too. Both, on purpose: the scheduler avoids waking a worker for
 * nothing, and the engine catches the case where the session ended between being scheduled
 * and being run.
 */
fun interface SyncGate {

    /** What this run is allowed to do. */
    suspend fun evaluate(): SyncVerdict
}

/**
 * The gate's answer.
 *
 * [NoSession] and [Unavailable] are both refusals; they differ only in whether the caller
 * should come back. Keeping them apart is what stops a transient problem from being filed
 * as a finished run.
 */
sealed interface SyncVerdict {

    /** Go ahead. */
    data object Allowed : SyncVerdict

    /**
     * There is genuinely no session, so there is nothing to sync and nothing to retry.
     * Signing in is what changes this, and the app is fully functional offline meanwhile
     * (SYNC_DESIGN §9).
     */
    data class NoSession(val reason: String) : SyncVerdict

    /**
     * We could not tell right now — a token that would not refresh, a maintenance window, a
     * store that would not open. Nothing about this is permanent, so the run is worth
     * repeating rather than recording as done.
     */
    data class Unavailable(val reason: String) : SyncVerdict
}
