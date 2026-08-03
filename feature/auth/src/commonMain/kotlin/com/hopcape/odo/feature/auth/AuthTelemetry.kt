package com.hopcape.odo.feature.auth

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.performance.api.currentTraceContext
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * Observability for auth, behind one intent-named surface — the same shape as the other
 * features' facades, so the session manager reads as its own logic rather than a wall of
 * logging calls.
 *
 * The event worth having is [sessionEnded]. A refresh token that stops working ends the
 * session with no error, no screen and no interruption — by design — so without a line here
 * an install can quietly stop syncing for days and look perfectly healthy.
 *
 * **Never log a token, a code or a phone number.** TDD §12 puts OTPs and tokens in the same
 * bucket as bill contents. Nothing below takes a value: only that something happened, and
 * for a failure its error *type*.
 */
internal class AuthTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
) {

    /** A code was asked for. Not which number it went to. */
    suspend fun otpRequested() = log(EVENT_OTP_REQUESTED)

    /** A code was rejected — wrong, expired, or asked for too often. */
    suspend fun otpRejected(reason: Any) =
        log(EVENT_OTP_REJECTED, mapOf(Key.REASON to reason::class.simpleName))

    /** Someone signed in. The funnel's end, and the moment sync becomes possible. */
    suspend fun signedIn() {
        log(EVENT_SIGNED_IN)
        analytics.track(EVENT_SIGNED_IN, emptyMap())
    }

    /** Someone signed out deliberately. */
    suspend fun signedOut() {
        log(EVENT_SIGNED_OUT)
        analytics.track(EVENT_SIGNED_OUT, emptyMap())
    }

    /**
     * The session ended on its own — the refresh token was refused.
     *
     * Recorded as a warning rather than an error: it is an expected end state, not a fault.
     * But it stops sync silently, which is why it is reported at all.
     */
    suspend fun sessionEnded() {
        logger.warn(TAG, EVENT_SESSION_ENDED, tc = currentTraceContext().toLog())
        analytics.track(EVENT_SESSION_ENDED, emptyMap())
    }

    /**
     * This build's SMS Retriever signature hash, logged so the sending template can carry it.
     *
     * Recorded because the failure it prevents is invisible: a message whose text lacks the
     * hash is never delivered to the app, with no error and no callback, which from inside
     * looks exactly like an SMS that never arrived. There is otherwise no way to find out
     * what the value is for a given build, and debug and release differ.
     *
     * The hash is derived from the signing certificate, which is public, so it is not a
     * secret. Logged at info once per code screen rather than tracked as analytics: it is a
     * fact about the build, not something an owner did.
     */
    suspend fun smsSignature(hashes: List<String>) =
        log(EVENT_SMS_SIGNATURE, mapOf(Key.HASHES to hashes.joinToString()))

    /** A stored session was found and reused, so this launch needed no network. */
    suspend fun sessionRestored() = log(EVENT_SESSION_RESTORED)

    /**
     * Sign-in was declined — from either screen, and back counts as declining.
     *
     * The biggest drop-off in the flow, and the session manager cannot see it: nothing was
     * requested and nothing failed. Sync stays off for everyone who lands here, which makes
     * this the number that says how much of the install base is never backed up.
     */
    suspend fun signInSkipped(step: String) {
        log(EVENT_SKIPPED, mapOf(Key.STEP to step))
        analytics.track(EVENT_SKIPPED, mapOf(Key.STEP to step))
    }

    /**
     * Three wrong codes, so the owner was sent back to correct the number.
     *
     * A dead end reached by someone who *was* trying to sign in — worth telling apart from
     * skipping, because the fix is different: a wrong number, or codes not arriving.
     */
    suspend fun otpAttemptsExhausted() {
        log(EVENT_ATTEMPTS_EXHAUSTED)
        analytics.track(EVENT_ATTEMPTS_EXHAUSTED, emptyMap())
    }

    private suspend fun log(event: String, fields: Map<String, Any?> = emptyMap()) =
        logger.info(TAG, event, tc = currentTraceContext().toLog(), fields = fields)

    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    internal object Key {
        const val REASON = "reason"
        const val STEP = "step"
        const val HASHES = "hashes"
    }

    /** Which screen the owner walked away from. */
    internal object Step {
        const val PHONE = "phone"
        const val OTP = "otp"
    }

    internal companion object {
        const val TAG = "auth"

        /* Event names. Once shipped these are what the dashboard queries — do not rename. */
        const val EVENT_OTP_REQUESTED = "auth_otp_requested"
        const val EVENT_OTP_REJECTED = "auth_otp_rejected"
        const val EVENT_SIGNED_IN = "auth_signed_in"
        const val EVENT_SIGNED_OUT = "auth_signed_out"
        const val EVENT_SESSION_ENDED = "auth_session_ended"
        const val EVENT_SESSION_RESTORED = "auth_session_restored"
        const val EVENT_SKIPPED = "auth_skipped"
        const val EVENT_ATTEMPTS_EXHAUSTED = "auth_attempts_exhausted"
        const val EVENT_SMS_SIGNATURE = "auth_sms_signature"
    }
}
