package com.hopcape.logging.internal.upload

import com.hopcape.logging.api.DiagnosticRequests
import com.hopcape.logging.api.LogFileStore
import com.hopcape.logging.api.LogUploadOutcome
import com.hopcape.logging.api.LogUploadResult
import com.hopcape.logging.api.LogUploadRunner
import com.hopcape.logging.api.LogUploadTarget
import com.hopcape.logging.api.Logger
import kotlin.concurrent.Volatile

/**
 * The mechanical half of upload (docs/LOGGING_PLAN.md §7.1). [logger] and [store] are
 * whatever `loggingModule` resolves them to — see that binding's comment on why [store] is
 * deliberately a *different* `LogFileStore` instance than the one wired into the sink chain,
 * and why that is safe for what this class does with it (list, read, delete — never seal).
 *
 * [target] is `null` on a build with no configured upload destination (an unconfigured
 * Supabase project) — every pass is [LogUploadOutcome.Skipped] in that case, the same as
 * every other Supabase adapter's absence.
 *
 * [requests] is the diagnostics outbox. A pass asks it for the oldest request still waiting
 * and files every file it uploads under that reference, so the code in somebody's support
 * mail finds their logs. It is `null` on a build with no local database bound, and the pass
 * then uploads without a reference rather than not uploading.
 */
internal class LogUploadCoordinator(
    private val logger: Logger,
    private val store: LogFileStore,
    private val target: LogUploadTarget?,
    private val requests: DiagnosticRequests? = null,
) : LogUploadRunner {

    @Volatile
    private var autoUploadConsentGranted: Boolean = false

    override fun setAutoUploadConsent(granted: Boolean) {
        autoUploadConsentGranted = granted
    }

    override suspend fun uploadPending(isManual: Boolean): LogUploadOutcome {
        val outcome = runPass(isManual)
        // A pass finishing quietly — including a skip — is the offline-first failure mode
        // that matters: per-file failures already reach SupabaseTelemetry, but nothing else
        // says whether a pass ran at all, or why it didn't. One line, every exit path, not
        // just the ones that got as far as attempting an upload.
        logger.info(
            TAG,
            "upload_pass.done",
            fields = mapOf(
                "isManual" to isManual,
                "outcome" to outcome::class.simpleName,
                "processed" to (outcome as? LogUploadOutcome.Delivered)?.count,
            ),
        )
        return outcome
    }

    private suspend fun runPass(isManual: Boolean): LogUploadOutcome {
        val uploadTarget = target ?: return LogUploadOutcome.Skipped
        if (!isManual && !autoUploadConsentGranted) return LogUploadOutcome.Skipped

        // Seals whatever is currently open (FileSink implements Sealable; AsyncSink acts on
        // it only for this explicit flush — see that class's doc) so "upload now" genuinely
        // means everything logged so far, not just what had already rotated naturally.
        logger.flush()

        // Read before the first upload, not per file: every file this pass sends belongs to
        // the same request, and a request opened mid-pass belongs to the next one.
        val reference = requests?.oldestOpen()

        val sealedFiles = store.listSealed()
        var processed = 0
        var anyLeftForRetry = false

        for (file in sealedFiles) {
            val bytes = store.read(file.name) ?: continue // deleted from under us; nothing to do

            when (uploadTarget.upload(file, bytes, reference)) {
                LogUploadResult.DELIVERED -> {
                    store.delete(file.name)
                    processed++
                }
                LogUploadResult.REJECTED -> {
                    // A permanent failure deletes anyway — one poisoned file must not wedge
                    // every future pass (same reasoning as analytics' dead-letter).
                    store.delete(file.name)
                    processed++
                }
                LogUploadResult.RETRY -> anyLeftForRetry = true
            }
        }

        settle(reference, anyLeftForRetry)

        return if (anyLeftForRetry) LogUploadOutcome.Partial else LogUploadOutcome.Delivered(processed)
    }

    /**
     * Closes the request out, or records that this attempt did not finish it.
     *
     * A request is only delivered once nothing is left behind. Closing it on a partial pass
     * would tell support the logs are there while some are still on the phone, and that is
     * the failure this whole path exists to avoid.
     */
    private suspend fun settle(reference: String?, anyLeftForRetry: Boolean) {
        if (reference == null) return
        val outbox = requests ?: return
        if (anyLeftForRetry) {
            outbox.markAttemptFailed(reference, RETRY_REASON)
        } else {
            outbox.markDelivered(reference)
        }
    }

    private companion object {
        const val TAG = "LogUpload"
        const val RETRY_REASON = "files left for retry"
    }
}
