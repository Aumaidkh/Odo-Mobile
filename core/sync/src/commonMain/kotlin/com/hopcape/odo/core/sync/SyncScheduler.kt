package com.hopcape.odo.core.sync

/**
 * Asks the platform to run a sync "soon", subject to its own constraints and backoff.
 *
 * This is Odo's one real divergence from Now in Android: NiA is Android-only, so
 * WorkManager is simply *there*. Odo's engine is `commonMain`, so only the scheduling is
 * platform-specific — Android backs this with unique WorkManager work
 * (`ExistingWorkPolicy.KEEP`, `NetworkType.CONNECTED`, exponential backoff), iOS with
 * `BGTaskScheduler` in Phase 2.
 *
 * Declared here rather than in the module that implements it so that a repository can ask
 * for a sync after a local write without depending on WorkManager, and the `:app`
 * bootstrap can trigger the startup run without depending on either.
 *
 * Design: [docs/SYNC_DESIGN.md] §10.
 */
interface SyncScheduler {

    /** Enqueue the once-per-launch run. Idempotent: a run already queued is kept, not stacked. */
    fun scheduleStartupSync()

    /** Ask for a run now-ish. Coalesced with anything already pending, except [SyncReason.Manual]. */
    fun requestSync(reason: SyncReason)
}

/**
 * Why a sync was requested. Not just telemetry — it decides pacing: a burst of local
 * writes is debounced into one run, while a user pulling to refresh expects their tap to
 * do something immediately.
 */
enum class SyncReason {
    /** App start or return to foreground. */
    AppForeground,

    /** A local write happened; debounced and coalesced (~5s) so three edits fire one sync. */
    LocalWrite,

    /** An FCM data push told us the server changed. */
    RemoteChange,

    /**
     * The device got a usable network back while the app was open.
     *
     * Separate from [AppForeground] so the log says which of the two happened. On Android a
     * job already queued would have waited for the network anyway — its constraint says so —
     * but nothing was queued when the last run finished for lack of one, and the owner is
     * looking at the screen now.
     */
    Reconnected,

    /** The user explicitly asked. Bypasses the debounce. */
    Manual,

    /** A session just started — includes adopting pre-auth local data (SYNC_DESIGN §9). */
    SignIn,
}
