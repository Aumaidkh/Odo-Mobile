package com.hopcape.odo.feature.healthscore.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace
import kotlin.coroutines.CoroutineContext

/**
 * All observability for the health score, behind intent-named methods, so the ViewModel
 * reads as the screen's logic instead of a wall of logger, analytics and tracer calls.
 *
 * One class owns the three ports, the taxonomy ([Event], [Key]) and the trace plumbing:
 * [flowTrace] is minted per instance (a Koin `factory`, so one instance covers one visit),
 * and [op] returns the child-trace context to launch an async op under.
 *
 * **No PII.** A score, a band, a factor's name and booleans only — never a registration
 * number, a workshop, or what a document says. The score is a derived number about a car,
 * not a fact about a person.
 *
 * Every method is fire-and-forget: nothing here returns a decision, so instrumentation
 * cannot change what the screen does.
 */
internal class HealthScoreTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    @Suppress("unused") private val tracer: PerformanceTracer,
    private val ids: IdGenerator,
) {

    private val flowTraceId: String = "${FLOW}_${ids.newId()}"
    private val flowTrace = PerfTrace(flowId = FLOW, traceId = flowTraceId)

    /** The child-trace context to `launch(...)` an async op under. */
    fun op(name: String): CoroutineContext = flowTrace.withNewTrace("${name}_${ids.newId()}")

    /**
     * The screen was opened, with the score it opened on.
     *
     * [band] is what the product cares about in aggregate — how many cars sit in each
     * band is the shape of the whole user base's maintenance behaviour, and it is what the
     * resale upsell targets. [hasNothingLogged] separates a genuine low score from a car
     * nobody has told Odo anything about, which is a very different problem to fix.
     */
    fun scoreOpened(score: Int, band: String, isPro: Boolean, hasNothingLogged: Boolean) {
        val fields = mapOf(
            Key.SCORE to score,
            Key.BAND to band,
            Key.IS_PRO to isPro,
            Key.NOTHING_LOGGED to hasNothingLogged,
        )
        analytics.track(Event.SCORE_OPENED, fields)
        logger.info(TAG, Event.SCORE_OPENED, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * The explainer was opened. Owners reaching for "how is this calculated" is the signal
     * that the number is not explaining itself.
     */
    fun infoOpened(score: Int) {
        val fields = mapOf(Key.SCORE to score)
        analytics.track(Event.INFO_OPENED, fields)
        logger.debug(TAG, Event.INFO_OPENED, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * "Unlock with Pro" was tapped — the paywall funnel's step from this screen, and the
     * reason the locked breakdown exists. [score] rides along because whether a low or a
     * high score converts better decides which one the upsell should target.
     */
    fun unlockTapped(score: Int) {
        val fields = mapOf(Key.SCORE to score)
        analytics.track(Event.UNLOCK_TAPPED, fields)
        logger.info(TAG, Event.UNLOCK_TAPPED, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * A read of the local DB failed.
     *
     * The local DB is the source of truth, so this is a broken record rather than a slow
     * network, and it is otherwise invisible twice over: the screen sits on whatever it was
     * showing, and an unhandled failure inside a `collect` takes the ViewModel's scope down.
     */
    fun readFailed(cause: Throwable) {
        val fields = mapOf(Key.REASON to (cause::class.simpleName ?: UNKNOWN))
        analytics.track(Event.READ_FAILED, fields)
        logger.error(TAG, Event.READ_FAILED, tc = flowTrace.toLog(), fields = fields)
    }

    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    private companion object {
        const val TAG = "HEALTH"
        const val FLOW = "health"
        const val UNKNOWN = "Unknown"
    }

    /*
     * The feature's observability taxonomy. These names are what a dashboard queries, so
     * they are shipped contracts: reuse one rather than inventing a synonym, and do not
     * rename one without accepting that its history stops there.
     */

    /** Analytics event names. Every one is declared in `healthScoreAnalyticsEvents`. */
    object Event {
        const val SCORE_OPENED = "health_score_opened"
        const val INFO_OPENED = "health_info_opened"
        const val UNLOCK_TAPPED = "health_unlock_tapped"
        const val READ_FAILED = "health_read_failed"
    }

    /** Property names carried by the events above. */
    object Key {
        const val SCORE = "score"
        const val BAND = "band"
        const val IS_PRO = "is_pro"
        const val NOTHING_LOGGED = "nothing_logged"
        const val REASON = "reason"
    }
}
