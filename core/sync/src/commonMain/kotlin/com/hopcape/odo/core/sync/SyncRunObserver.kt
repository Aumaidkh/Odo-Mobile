package com.hopcape.odo.core.sync

/**
 * Told when a run starts and stops, so something can render "Syncing…".
 *
 * A run in flight is the one piece of sync state that is not a row in a table, so it cannot
 * be queried like the pending count can — the engine has to say. Declared here and bound by
 * whoever owns the status the UI reads.
 *
 * Deliberately not `suspend` and deliberately returning nothing: this is called from the
 * engine's bookends, and an observer that could fail or block would be able to change
 * whether a sync happens.
 */
fun interface SyncRunObserver {

    /** `true` when a run begins, `false` when it ends — however it ended. */
    fun onRunning(running: Boolean)
}
