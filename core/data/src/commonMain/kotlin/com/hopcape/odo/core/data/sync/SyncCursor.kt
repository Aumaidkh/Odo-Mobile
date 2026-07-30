package com.hopcape.odo.core.data.sync

import kotlin.time.Instant

/**
 * One entity's sync bookmark — the `sync_state` row as a Kotlin value.
 *
 * [lastPulledAt] is the high-water mark of server `updated_at` values already applied
 * locally, and it is what makes a pull a *delta* rather than a full table read. It is
 * committed in the same transaction as the rows it covers, so a run killed halfway leaves
 * a cursor that is consistent with what actually landed — the next run simply resumes.
 *
 * Note the delta query uses `>=` against this value minus a small overlap, never `>`:
 * two rows can share an `updated_at` and clock skew is real, so re-reading a few rows
 * (idempotent, free) is always preferable to silently skipping one (permanent loss).
 *
 * Design: [docs/SYNC_DESIGN.md] §4.2, §6.2.
 */
internal data class SyncCursor(
    val entity: SyncEntity,
    val lastPulledAt: Instant? = null,
    val lastPushedAt: Instant? = null,
    val lastError: String? = null,
)
