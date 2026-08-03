package com.hopcape.odo.core.domain.sync

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Port exposing what the UI is allowed to know about background sync.
 *
 * A domain port — not a `:core:data` type — so a feature can render "Syncing…" or a
 * "3 changes not backed up" chip without depending on the data layer or knowing that a
 * sync engine exists. Same Ports & Adapters shape as
 * [com.hopcape.odo.core.domain.owner.SessionStatusProvider].
 *
 * Deliberately read-only: nothing in the UI *triggers* a sync through this port. Sync is
 * scheduled (app start, connectivity, a local write, a push), and a manual refresh goes
 * through the scheduler, not through a status observer.
 *
 * Design: [docs/SYNC_DESIGN.md] §5. No implementation exists yet — the engine lands in M5.
 */
interface SyncStatusProvider {

    /** `true` while a sync run is in flight. Drives the top-of-screen progress affordance. */
    val isSyncing: Flow<Boolean>

    /**
     * How many local rows are still waiting to reach the server (`sync_status = PENDING`).
     * Surfaced honestly rather than hidden: "not backed up yet" is the user's business,
     * especially before they've signed in at all.
     */
    val pendingCount: Flow<Int>

    /** When the last run completed successfully, or `null` if it never has on this install. */
    val lastSyncedAt: Flow<Instant?>

    /**
     * Why syncing is currently stuck, or `null` when nothing is wrong.
     *
     * A diagnostic string, not product copy — a failure type and, for a refused request, its
     * status. It exists because a pending count on its own cannot say whether the app is
     * mid-upload or has been refused the same way for three days, and those look identical
     * to whoever is asking why their data is missing.
     *
     * Never a server message: those quote the row that was refused, and these rows are the
     * owner's records.
     */
    val lastError: Flow<String?>
}
