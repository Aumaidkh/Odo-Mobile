package com.hopcape.logging.api

/**
 * The port an outside module implements to actually schedule upload work — WorkManager on
 * Android (`:core:platform`'s `WorkManagerLogUploadScheduler`). Mirrors `SyncScheduler`'s
 * shape (`:core:sync`) for the same reason: this module knows nothing about WorkManager, so
 * scheduling is a port like everything else platform-specific.
 */
@StableLoggerApi
interface LogUploadScheduler {
    /** Enqueues the recurring, consent-gated upload pass. Safe to call repeatedly — the
     *  platform implementation keeps it to one recurring job. */
    fun schedulePeriodic()

    /** Enqueues a one-shot upload that runs regardless of auto-upload consent — "Send
     *  diagnostics" is an explicit user action (D3, plan §1). */
    fun requestUploadNow()

    /** Cancels both. */
    fun cancel()
}
