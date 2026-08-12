package com.hopcape.odo.core.sync

/**
 * Whether a sync run is allowed to happen at all right now.
 *
 * Today this answers "is someone signed in": `owner_id` is stamped server-side from
 * `auth.uid()`, so an anonymous run cannot write anything and would only collect 401s
 * (SYNC_DESIGN §9).
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

    /** `true` when a run can proceed. */
    suspend fun canSync(): Boolean
}
