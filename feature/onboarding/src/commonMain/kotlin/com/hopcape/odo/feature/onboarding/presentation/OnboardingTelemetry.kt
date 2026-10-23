package com.hopcape.odo.feature.onboarding.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.TraceContext as LogTraceSource
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * Observability for the first-run pitch — the Welcome screen and the video intro.
 *
 * The setup steps have their own facade in `:feature:questionnaire`, with its own flow id.
 * The two funnels are joined by the session rather than by one trace: keeping a single trace
 * across both modules would need a shared seam in `:core:*` that nothing else wants.
 *
 * A Koin `factory`, so one instance covers one visit to the pitch.
 */
internal class OnboardingTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    private val tracer: PerformanceTracer,
    ids: IdGenerator,
) {

    private val flowTrace = PerfTrace(flowId = FLOW, traceId = "${FLOW}_${ids.newId()}")

    /** [variant] is a [Variant] value on the v2 Welcome, and null on v1. */
    fun welcomeShown(variant: String? = null) {
        val fields = variant?.let { mapOf(Key.VARIANT to it) }.orEmpty()
        analytics.track(Event.WELCOME_SHOWN, fields)
        logger.info(TAG, Event.WELCOME_SHOWN, tc = flowTrace.toLog(), fields = fields)
    }

    /** [makeSource] is a [MakeSource] value on v2. The make is not personal data. */
    fun welcomeCompleted(makeSource: String? = null, make: String? = null) {
        val fields = buildMap {
            makeSource?.let { put(Key.MAKE_SOURCE, it) }
            make?.let { put(Key.MAKE, it) }
        }
        analytics.track(Event.WELCOME_COMPLETED, fields)
        logger.info(TAG, Event.WELCOME_COMPLETED, tc = flowTrace.toLog(), fields = fields)
    }

    /** "Continue setup" on S1r. [step] is where setup reopens: `model` or `year_fuel`. */
    fun resumed(step: String) {
        analytics.track(Event.RESUMED, mapOf(Key.STEP to step))
        logger.info(TAG, Event.RESUMED, tc = flowTrace.toLog(), fields = mapOf(Key.STEP to step))
    }

    /** "Pick a different car" on S1r: the draft is dropped. */
    fun resumeReset() {
        analytics.track(Event.RESUME_RESET)
        logger.info(TAG, Event.RESUME_RESET, tc = flowTrace.toLog())
    }

    fun makeOtherOpened() {
        analytics.track(Event.MAKE_OTHER_OPENED)
        logger.info(TAG, Event.MAKE_OTHER_OPENED, tc = flowTrace.toLog())
    }

    /** Times the make read, answering null and logging when it fails. */
    suspend fun <T> makesLoad(read: suspend () -> T): T? {
        val span = tracer.startSpan(Trace.MAKES_LOAD, flowTrace.traceId ?: FLOW)
        return try {
            runCatchingCancellableSuspend { read() }
                .onFailure { e ->
                    span.setAttribute(Key.OUTCOME, OUTCOME_FAILED)
                    logger.error(
                        TAG,
                        Event.MAKES_LOAD_FAILED,
                        tc = flowTrace.toLog(),
                        fields = mapOf(Key.ERROR to (e::class.simpleName ?: "Unknown")),
                    )
                }
                .getOrNull()
        } finally {
            tracer.endSpan(span)
        }
    }

    /**
     * The owner said they already have an account and went to sign in instead of setting up.
     *
     * Counted separately from [welcomeCompleted]: it is a different funnel with a different
     * end, and reading them as one would hide how many first-run installs are actually
     * returning owners.
     */
    fun signInFromWelcome() {
        analytics.track(Event.WELCOME_SIGN_IN)
        logger.info(TAG, Event.WELCOME_SIGN_IN, tc = flowTrace.toLog())
    }

    /** Which legal page was opened — the only property is *which*, never who opened it. */
    fun legalOpened(document: String) {
        analytics.track(Event.LEGAL_OPENED, mapOf(Key.DOCUMENT to document))
        logger.debug(TAG, Event.LEGAL_OPENED, tc = flowTrace.toLog(), fields = mapOf(Key.DOCUMENT to document))
    }

    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    private companion object {
        const val TAG = "ONBOARDING"
        const val FLOW = "onboarding"
        const val OUTCOME_FAILED = "failed"
    }

    /** Event names are shipped contracts: reuse one rather than inventing a synonym. */
    object Event {
        const val WELCOME_SHOWN = "onboarding_welcome_shown"
        const val WELCOME_COMPLETED = "onboarding_welcome_completed"
        const val WELCOME_SIGN_IN = "onboarding_welcome_sign_in"
        const val LEGAL_OPENED = "onboarding_legal_opened"
        const val MAKE_OTHER_OPENED = "onboarding_make_other_opened"
        const val RESUMED = "onboarding_resumed"
        const val RESUME_RESET = "onboarding_resume_reset"
        const val MAKES_LOAD_FAILED = "onboarding_makes_load_failed"
    }

    object Trace {
        const val MAKES_LOAD = "onboarding_makes_load"
    }

    object Key {
        const val DOCUMENT = "document"
        const val VARIANT = "variant"
        const val STEP = "step"
        const val MAKE_SOURCE = "make_source"
        const val MAKE = "make"
        const val OUTCOME = "outcome"
        const val ERROR = "error"
    }

    /** The values [Key.VARIANT] takes. */
    object Variant {
        const val FRESH = "fresh"
        const val RESUME = "resume"
    }

    /** The values [Key.MAKE_SOURCE] takes. */
    object MakeSource {
        const val TILE = "tile"
        const val OTHER = "other"
    }
}
