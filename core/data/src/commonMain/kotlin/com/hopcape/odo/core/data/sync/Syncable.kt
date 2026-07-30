package com.hopcape.odo.core.data.sync

/**
 * A table that reconciles itself with the server. Implemented by the `OfflineFirst*`
 * repositories — the same objects that own the local writes, since they are the only
 * code that knows how a row maps to a DTO.
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
internal interface Syncable {

    /** Which table this syncs, which is also its position in the FK ordering. */
    val entity: SyncEntity

    /** `true` if this entity is fully reconciled; `false` asks the scheduler to retry the run. */
    suspend fun syncWith(synchronizer: Synchronizer): Boolean
}
