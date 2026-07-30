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
 * [syncWith] is **push then pull**, in that order: pushing first means the pull's
 * last-write-wins comparison sees our newest local version and cannot resurrect a stale
 * server row over an unsent edit.
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

    /** `true` if this entity is fully reconciled; `false` asks the scheduler to retry the run. */
    suspend fun syncWith(synchronizer: Synchronizer): Boolean
}
