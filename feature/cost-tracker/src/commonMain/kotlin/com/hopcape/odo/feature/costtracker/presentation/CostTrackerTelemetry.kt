package com.hopcape.odo.feature.costtracker.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.TraceContext as PerfTrace
import com.hopcape.logging.api.TraceContext as LogTrace
import kotlin.coroutines.CoroutineContext

/**
 * All observability for the cost tracker, behind intent-named methods, so the ViewModel
 * reads as the screen's logic instead of a wall of logger, analytics and tracer calls.
 *
 * One class owns the three ports, the taxonomy ([Event], [Key]) and the trace plumbing:
 * [flowTrace] is minted per instance (a Koin `factory`, so one instance covers one visit),
 * and [op] returns the child-trace context to launch an async op under.
 *
 * **No PII.** Periods, counts, booleans and the *source* of a fuel price only — never a
 * city, a registration number or what the owner paid at their pump. Distance is reported
 * because it is the reason a rate exists or does not; a single odometer figure is not.
 *
 * Every method is fire-and-forget: nothing here returns a decision, so instrumentation
 * cannot change what the screen does.
 */
internal class CostTrackerTelemetry(
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
     * The screen was opened, with whether it could actually answer.
     *
     * [hasRate] is the feature's own success measure: a cost tracker that cannot show a
     * ₹/km — too little distance, or no odometer readings — is a screen doing nothing for
     * the owner, and how often that happens is the number worth watching. [fuelEstimated]
     * separates the owners whose figure includes fuel from the ones seeing maintenance only.
     */
    fun costOpened(period: String, hasRate: Boolean, fuelEstimated: Boolean, kmDriven: Int) {
        val fields = mapOf(
            Key.PERIOD to period,
            Key.HAS_RATE to hasRate,
            Key.FUEL_ESTIMATED to fuelEstimated,
            Key.KM_DRIVEN to kmDriven,
        )
        analytics.track(Event.COST_OPENED, fields)
        logger.info(TAG, Event.COST_OPENED, tc = flowTrace.toLog(), fields = fields)
    }

    /** Which window owners actually look at — the chips are the only control on the screen. */
    fun periodChanged(period: String) {
        val fields = mapOf(Key.PERIOD to period)
        analytics.track(Event.PERIOD_CHANGED, fields)
        logger.debug(TAG, Event.PERIOD_CHANGED, tc = flowTrace.toLog(), fields = fields)
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
        const val TAG = "COST"
        const val FLOW = "cost"
        const val UNKNOWN = "Unknown"
    }

    /*
     * The feature's observability taxonomy. These names are what a dashboard queries, so
     * they are shipped contracts: reuse one rather than inventing a synonym, and do not
     * rename one without accepting that its history stops there.
     */

    /** Analytics event names. Every one is declared in `costTrackerAnalyticsEvents`. */
    object Event {
        const val COST_OPENED = "cost_opened"
        const val PERIOD_CHANGED = "cost_period_changed"
        const val READ_FAILED = "cost_read_failed"
    }

    /** Property names carried by the events above. */
    object Key {
        const val PERIOD = "period"
        const val HAS_RATE = "has_rate"
        const val FUEL_ESTIMATED = "fuel_estimated"
        const val KM_DRIVEN = "km_driven"
        const val REASON = "reason"
    }
}
