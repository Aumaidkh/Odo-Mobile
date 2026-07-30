package com.hopcape.odo.core.data.sync

/**
 * What a [Syncable] is allowed to ask the engine for while it syncs — its cursor, and
 * somewhere to record why it failed. Nothing else.
 *
 * Keeping this surface tiny is the whole point: a repository that can only read and
 * advance its own bookmark cannot accidentally reorder entities, retry on its own
 * schedule, or decide the run is over. Those are the engine's and the scheduler's
 * decisions (SYNC_DESIGN §5, §10).
 *
 * Modelled on Now in Android's `Synchronizer`, with its change-list versions replaced by
 * timestamp cursors — Supabase gives us `updated_at > cursor` directly, so Odo needs no
 * custom change-list endpoint.
 *
 * Design: [docs/SYNC_DESIGN.md] §5.
 */
internal interface Synchronizer {

    /** This entity's bookmark; a never-synced entity gets a cursor with null timestamps. */
    suspend fun cursor(entity: SyncEntity): SyncCursor

    /**
     * Advance the bookmark. Callers pass a transform rather than a value so the engine
     * can apply it inside the same transaction as the rows it describes — a partially
     * applied pull must never leave a cursor claiming more than landed.
     */
    suspend fun updateCursor(entity: SyncEntity, update: SyncCursor.() -> SyncCursor)

    /** Record why this entity failed, for the debug surface and the next run's logs. */
    suspend fun recordFailure(entity: SyncEntity, cause: Throwable)
}
