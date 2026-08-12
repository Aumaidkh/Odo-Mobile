package com.hopcape.odo.infrastructure.database.sync

/**
 * The `sync_status` column's vocabulary, mirrored in Kotlin.
 *
 * There is deliberately **no `FAILED`**: a push that failed needs exactly the same
 * treatment as one that never ran (retry on the next sync), and a separate state only
 * creates a way for a row to fall out of the outbox and never be picked up again. Retry
 * pacing belongs to the scheduler, not to a row.
 *
 * Design: [docs/SYNC_DESIGN.md] §4.1.
 */
internal enum class SyncStatus {
    /** Local mutation the server hasn't accepted yet. The outbox pushes these. */
    PENDING,

    /** The server holds this exact version; the row's `remote_version` says which. */
    SYNCED,

    /**
     * Reserved for Phase 2 multi-device merges. **The MVP never writes this** —
     * last-write-wins resolves every case (SYNC_DESIGN §7). It exists now so the column
     * doesn't need a migration when real merge semantics arrive.
     */
    CONFLICT,
}
