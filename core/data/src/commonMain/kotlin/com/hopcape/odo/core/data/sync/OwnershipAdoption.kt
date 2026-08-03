package com.hopcape.odo.core.data.sync

import kotlin.time.Instant

/**
 * Hands everything created before sign-in to the account that just signed in.
 *
 * Odo lets someone add a car, log services and scan bills with no account at all — a
 * placeholder `owner_id` stamps those rows. None of them can sync: the server derives
 * `owner_id` from `auth.uid()` by trigger, and RLS rejects anything else. So the first
 * successful sign-in moves them across, and they then flow up through the ordinary outbox
 * with no special-case upload path.
 *
 * Design: [docs/SYNC_DESIGN.md] §9.
 */
interface OwnershipAdoption {

    /**
     * Re-stamp every row still owned by the placeholder, and mark it `PENDING` so the
     * outbox picks it up.
     *
     * **Idempotent and safe to re-run.** It matches on the placeholder id, so a second call
     * finds nothing and does nothing — which is what makes it safe to call before every
     * sync rather than needing a "have we adopted yet" flag that could itself be wrong. A
     * crash halfway leaves some rows moved and some not, and the next call finishes the job.
     */
    suspend fun adopt(realOwnerId: String, now: Instant)
}
