package com.hopcape.odo.feature.fairnesscheck.presentation.report

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.performance.api.PerformanceTracer
import kotlin.coroutines.CoroutineContext
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * All observability for the fairness check, behind intent-named methods, so the ViewModel
 * reads as the flow's logic instead of a wall of logger, analytics and tracer calls.
 *
 * What this feature needs measured is unusual: the interesting number is not how many checks
 * ran but **how many could say anything at all**. A pool with no benchmark for a category, or
 * three data points for a city, produces a screen that admits it knows nothing — and how
 * often that happens is what decides where seed data goes next.
 *
 * **No PII.** Outcome type names, sample sizes and counts only — never a workshop, a line
 * item's wording, or what a bill came to.
 *
 * Every method is fire-and-forget: nothing here returns a decision, so instrumentation
 * cannot change what the screen does.
 */
internal class FairnessTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    @Suppress("unused") private val tracer: PerformanceTracer,
    private val ids: IdGenerator,
) {

    private val flowTraceId: String = "${FLOW}_${ids.newId()}"
    private val flowTrace = PerfTrace(flowId = FLOW, traceId = flowTraceId)

    /** The child-trace context to `launch(...)` the benchmark lookup under. */
    fun op(name: String): CoroutineContext = flowTrace.withNewTrace("${name}_${ids.newId()}")

    /**
     * A check finished. [outcome] is the result's type name, [sampleSize] the weakest
     * comparison behind it, [lineCount] how many lines were sent and [benchmarkedLines] how
     * many of them the pool could actually price.
     *
     * The last two are the coverage measure: a scan whose lines all come back unpriced looks
     * identical to a fair bill on any count of checks, and only this ratio tells them apart.
     */
    fun checked(outcome: String, sampleSize: Int, lineCount: Int, benchmarkedLines: Int) {
        val fields = mapOf(
            Key.OUTCOME to outcome,
            Key.SAMPLE_SIZE to sampleSize,
            Key.LINE_COUNT to lineCount,
            Key.BENCHMARKED_LINES to benchmarkedLines,
        )
        analytics.track(Event.CHECKED, fields)
        logger.info(TAG, Event.CHECKED, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * The check ran with no city on the owner's profile, so nothing was benchmarked. Worth
     * counting: it is a gap in onboarding showing up as a dead end here.
     */
    fun skippedWithoutCity() {
        analytics.track(Event.NO_CITY, emptyMap())
        logger.info(TAG, Event.NO_CITY, tc = flowTrace.toLog())
    }

    /**
     * The benchmark lookup threw. Different from finding no data: the repository already
     * swallows a failing source into an empty result, so reaching here means something
     * broke that nobody expected.
     */
    fun checkFailed(cause: Throwable) {
        val fields = mapOf(Key.REASON to (cause::class.simpleName ?: UNKNOWN))
        analytics.track(Event.FAILED, fields)
        logger.error(TAG, Event.FAILED, tc = flowTrace.toLog(), fields = fields)
    }

    /** "Report overcharge" was tapped — the end of the funnel this screen exists to drive. */
    fun reportTapped() {
        analytics.track(Event.REPORT_TAPPED, emptyMap())
        logger.info(TAG, Event.REPORT_TAPPED, tc = flowTrace.toLog())
    }

    /** The no-city state sent the owner to their profile to set one. */
    fun setCityTapped() {
        analytics.track(Event.SET_CITY_TAPPED, emptyMap())
        logger.info(TAG, Event.SET_CITY_TAPPED, tc = flowTrace.toLog())
    }

    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    private companion object {
        const val TAG = "FAIRNESS"
        const val FLOW = "fairness"
        const val UNKNOWN = "Unknown"
    }

    /*
     * The feature's observability taxonomy. These names are what a dashboard queries, so
     * they are shipped contracts: reuse one rather than inventing a synonym, and do not
     * rename one without accepting that its history stops there.
     */

    /** Analytics event names. Every one is declared in `fairnessCheckAnalyticsEvents`. */
    object Event {
        const val CHECKED = "fairness_checked"
        const val NO_CITY = "fairness_no_city"
        const val FAILED = "fairness_check_failed"
        const val REPORT_TAPPED = "fairness_report_tapped"
        const val SET_CITY_TAPPED = "fairness_set_city_tapped"
    }

    /** Property names carried by the events above. */
    object Key {
        const val OUTCOME = "outcome"
        const val SAMPLE_SIZE = "sample_size"
        const val LINE_COUNT = "line_count"
        const val BENCHMARKED_LINES = "benchmarked_lines"
        const val REASON = "reason"
    }
}
