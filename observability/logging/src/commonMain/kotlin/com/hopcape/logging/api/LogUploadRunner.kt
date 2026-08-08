package com.hopcape.logging.api

/**
 * What `LogUploadWorker` (`:core:platform`) resolves from Koin and runs — the mechanical
 * half of upload. `LogUploadScheduler` decides *when*; this does the work once asked.
 */
@StableLoggerApi
interface LogUploadRunner {
    /**
     * Seals the current file if anything is open, then attempts every sealed file against
     * the configured `LogUploadTarget`.
     *
     * [isManual] is true only for a "Send diagnostics"-style request — [requestUploadNow]
     * bypasses [setAutoUploadConsent] entirely (D3, plan §1); the periodic path passes
     * `false` and is skipped outright without consent. No `LogUploadTarget` configured
     * (an unconfigured Supabase build, plan §7.1) is [LogUploadOutcome.Skipped] either way.
     */
    suspend fun uploadPending(isManual: Boolean): LogUploadOutcome

    /** Grants or revokes consent for the periodic, non-manual path. Release builds start
     *  with this false (D3); debug/internal builds start true. */
    fun setAutoUploadConsent(granted: Boolean)
}

/** The result of one [LogUploadRunner.uploadPending] pass. */
@StableLoggerApi
sealed interface LogUploadOutcome {
    /** Every sealed file at the time of the pass was delivered (or permanently rejected —
     *  either way, none are left behind). [count] is how many files were processed. */
    data class Delivered(val count: Int) : LogUploadOutcome

    /** At least one file hit [com.hopcape.logging.api.LogUploadResult.RETRY] and was left
     *  in place for the next pass. */
    data object Partial : LogUploadOutcome

    /** Nothing was attempted — no target configured, or (non-manual only) consent not
     *  granted. Not a failure: retrying with backoff would just burn wakeups. */
    data object Skipped : LogUploadOutcome
}
