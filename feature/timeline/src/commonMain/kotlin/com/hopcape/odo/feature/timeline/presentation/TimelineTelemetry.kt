package com.hopcape.odo.feature.timeline.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace
import kotlin.coroutines.CoroutineContext

/**
 * All observability for the timeline, behind intent-named methods, so the ViewModels read
 * as the tab's logic instead of a wall of logger, analytics and tracer calls.
 *
 * One class owns the three ports, the taxonomy ([Event], [Key]) and the trace plumbing:
 * [flowTrace] is minted per instance (a Koin `factory`, so one instance covers one visit),
 * and [op] returns the child-trace context to launch an async op under.
 *
 * **No PII.** Counts, booleans and category names only — never a workshop, a registration
 * number, or what a document says. How many events a car has is a fact about a record; what
 * the record says is not this file's business.
 *
 * Every method is fire-and-forget: nothing here returns a decision, so instrumentation
 * cannot change what the screen does.
 */
internal class TimelineTelemetry(
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
     * The tab was opened, with the shape of the record it opened on.
     *
     * [eventCount] and [hasServices] are the feature's own success measure: the timeline
     * only pays off once there is a history on it, so a tab opened on an empty feed is a
     * different product problem from one opened on five years of bills.
     */
    fun timelineOpened(eventCount: Int, hasServices: Boolean, isNewUser: Boolean) {
        val fields = mapOf(
            Key.EVENT_COUNT to eventCount,
            Key.HAS_SERVICES to hasServices,
            Key.IS_NEW_USER to isNewUser,
        )
        analytics.track(Event.OPENED, fields)
        logger.info(TAG, Event.OPENED, tc = flowTrace.toLog(), fields = fields)
    }

    /** A service card was opened from the feed. */
    fun serviceOpened() {
        analytics.track(Event.SERVICE_OPENED, emptyMap())
        logger.debug(TAG, Event.SERVICE_OPENED, tc = flowTrace.toLog())
    }

    /**
     * "Add bill" was tapped on a self-reported entry — the timeline's contribution to the
     * North Star (bills scanned), and the reason unverified rows carry the prompt at all.
     */
    fun addBillTapped() {
        analytics.track(Event.ADD_BILL_TAPPED, emptyMap())
        logger.info(TAG, Event.ADD_BILL_TAPPED, tc = flowTrace.toLog())
    }

    /** "Scan first bill" on the empty feed — the same funnel from a car with no history. */
    fun scanFirstTapped() {
        analytics.track(Event.SCAN_FIRST_TAPPED, emptyMap())
        logger.info(TAG, Event.SCAN_FIRST_TAPPED, tc = flowTrace.toLog())
    }

    /** The record was shared from the timeline header. */
    fun shareTapped() {
        analytics.track(Event.SHARE_TAPPED, emptyMap())
        logger.info(TAG, Event.SHARE_TAPPED, tc = flowTrace.toLog())
    }

    /** The filter sheet was opened. */
    fun filterOpened() {
        analytics.track(Event.FILTER_OPENED, emptyMap())
        logger.debug(TAG, Event.FILTER_OPENED, tc = flowTrace.toLog())
    }

    /**
     * A filter was applied. [categories] is the set left on and [onlyFlagged] the switch —
     * which parts of the feed owners choose to hide is what decides whether the four
     * categories are the right four.
     */
    fun filterApplied(categories: Set<String>, onlyFlagged: Boolean) {
        val fields = mapOf(
            Key.CATEGORIES to categories.sorted().joinToString(","),
            Key.ONLY_FLAGGED to onlyFlagged,
        )
        analytics.track(Event.FILTER_APPLIED, fields)
        logger.info(TAG, Event.FILTER_APPLIED, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * A read of the local DB failed.
     *
     * The local DB is the source of truth, so this is a broken record rather than a slow
     * network, and it is otherwise invisible twice over: the tab sits on whatever it was
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
        const val TAG = "TIMELINE"
        const val FLOW = "timeline"
        const val UNKNOWN = "Unknown"
    }

    /*
     * The feature's observability taxonomy. These names are what a dashboard queries, so
     * they are shipped contracts: reuse one rather than inventing a synonym, and do not
     * rename one without accepting that its history stops there.
     */

    /** Analytics event names. Every one is declared in `timelineAnalyticsEvents`. */
    object Event {
        const val OPENED = "timeline_opened"
        const val SERVICE_OPENED = "timeline_service_opened"
        const val ADD_BILL_TAPPED = "timeline_add_bill_tapped"
        const val SCAN_FIRST_TAPPED = "timeline_scan_first_tapped"
        const val SHARE_TAPPED = "timeline_share_tapped"
        const val FILTER_OPENED = "timeline_filter_opened"
        const val FILTER_APPLIED = "timeline_filter_applied"
        const val READ_FAILED = "timeline_read_failed"
    }

    /** Property names carried by the events above. */
    object Key {
        const val EVENT_COUNT = "event_count"
        const val HAS_SERVICES = "has_services"
        const val IS_NEW_USER = "is_new_user"
        const val CATEGORIES = "categories"
        const val ONLY_FLAGGED = "only_flagged"
        const val REASON = "reason"
    }
}
