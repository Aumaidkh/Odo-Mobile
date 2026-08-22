package com.hopcape.odo.core.sync

/**
 * A table that reconciles itself with the server. Implemented in `:core:data` by the
 * `OfflineFirst*` repositories — the same objects that own the local writes, since they
 * are the only code that knows how a row maps to a DTO.
 *
 * That direction is the whole reason this module exists: `:core:data` depends on
 * `:core:sync`, never the reverse. An engine that reached *into* the data layer to sync
 * its tables would need `:core:data`, while the repositories would need the engine to
 * register with — a cycle Gradle would reject. Inverting it means the engine only ever
 * receives the [Syncable]s it was given.
 *
 * **Push and pull are separate calls, and the engine runs them as separate phases.** They
 * used to be one method, which forced both halves to share one failure rule — and the rule
 * had to be the strict one the push needs, because pushing a child before its parent is a
 * foreign-key error. That cost an owner their whole account view whenever the first entity
 * failed: `PROFILES` is first, so a profile that would not sync meant cars, service logs and
 * documents were never even fetched (issue #312). Split, each half gets the rule that is
 * actually true of it.
 *
 * Push still comes first, for every entity, before any pull runs. That ordering is what
 * keeps the pull's last-write-wins comparison from resurrecting a stale server row over an
 * unsent local edit.
 *
 * Returning `false` rather than throwing is Now in Android's shape and the right one — a
 * failed entity means the *run* retries, and WorkManager's backoff decides when. No
 * repository gets its own retry loop.
 *
 * Design: [docs/SYNC_DESIGN.md] §5, §6.
 */
interface Syncable {

    /** Which table this syncs, which is also its position in the FK ordering. */
    val entity: SyncEntity

    /**
     * Send local changes.
     *
     * `false` stops the push phase where it stands. Every entity after this one in
     * [SyncEntity] order may reference this table, and a child sent before its parent is a
     * foreign-key error — carrying on would turn one failure into six.
     */
    suspend fun pushTo(synchronizer: Synchronizer): Boolean

    /**
     * Apply the server's changes.
     *
     * `false` is recorded and the run is reported as [SyncResult.Partial], but the phase
     * **carries on to the next entity**. A pull has no ordering requirement: cars can be
     * fetched perfectly well when the profile fetch failed, and refusing to try is what made
     * a full account look like an empty one.
     */
    suspend fun pullFrom(synchronizer: Synchronizer): Boolean
}
