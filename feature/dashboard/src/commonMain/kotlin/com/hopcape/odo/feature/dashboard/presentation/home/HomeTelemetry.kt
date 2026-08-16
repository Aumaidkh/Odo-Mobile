package com.hopcape.odo.feature.dashboard.presentation.home

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace
import kotlin.coroutines.CoroutineContext

/**
 * All observability for Home, behind intent-named methods, so the ViewModel reads as the
 * tab's logic instead of a wall of logger, analytics and tracer calls.
 *
 * One class owns the three ports, the taxonomy ([Event], [Key]) and the trace plumbing:
 * [flowTrace] is minted per instance (a Koin `factory`, so one instance covers one visit),
 * and [op] returns the child-trace context to launch an async op under.
 *
 * **No PII.** Counts, bands, booleans and type names only — never the owner's name, a
 * registration number, a workshop, or what a document says. That a car has a lapsed PUC is
 * a fact about a record; which car and whose is not this file's business.
 *
 * Every method is fire-and-forget: nothing here returns a decision, so instrumentation
 * cannot change what the screen does.
 */
internal class HomeTelemetry(
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
     * The tab was opened, with the shape of the dashboard it opened on.
     *
     * The band and the new-user flag are the feature's own success measure: Home only pays
     * off once there is a record behind it, so a dashboard opened on an empty checklist is
     * a different product problem from one opened on a scored car. [hasAttention] says how
     * often the card that drives every renewal actually has something in it.
     */
    fun homeOpened(band: String, isNewUser: Boolean, hasAttention: Boolean, setupDone: Int) {
        val fields = mapOf(
            Key.BAND to band,
            Key.IS_NEW_USER to isNewUser,
            Key.HAS_ATTENTION to hasAttention,
            Key.SETUP_DONE to setupDone,
        )
        analytics.track(Event.OPENED, fields)
        logger.info(TAG, Event.OPENED, tc = flowTrace.toLog(), fields = fields)
    }

    /** The health card's "see breakdown" was tapped. */
    fun breakdownOpened() {
        analytics.track(Event.BREAKDOWN_OPENED, emptyMap())
        logger.debug(TAG, Event.BREAKDOWN_OPENED, tc = flowTrace.toLog())
    }

    /**
     * The attention card was acted on. [kind] is the case's type name, never the document
     * itself — which kinds of deadline owners actually tap is what decides whether the
     * ranking is the right ranking.
     */
    fun attentionTapped(kind: String) {
        val fields = mapOf(Key.KIND to kind)
        analytics.track(Event.ATTENTION_TAPPED, fields)
        logger.info(TAG, Event.ATTENTION_TAPPED, tc = flowTrace.toLog(), fields = fields)
    }

    /** The recent-activity row was opened. */
    fun recentOpened() {
        analytics.track(Event.RECENT_OPENED, emptyMap())
        logger.debug(TAG, Event.RECENT_OPENED, tc = flowTrace.toLog())
    }

    /** "Timeline" beside the recent heading. */
    fun timelineOpened() {
        analytics.track(Event.TIMELINE_OPENED, emptyMap())
        logger.debug(TAG, Event.TIMELINE_OPENED, tc = flowTrace.toLog())
    }

    /**
     * The scanner was opened from Home — the North Star funnel (bills scanned) as it starts
     * on the dashboard. [fromChecklist] separates a new owner working through setup from a
     * returning one scanning their latest bill.
     */
    fun scanBillTapped(fromChecklist: Boolean) {
        val fields = mapOf(Key.FROM_CHECKLIST to fromChecklist)
        analytics.track(Event.SCAN_BILL_TAPPED, fields)
        logger.info(TAG, Event.SCAN_BILL_TAPPED, tc = flowTrace.toLog(), fields = fields)
    }

    /** The checklist's documents row — the other half of the setup funnel. */
    fun addDocumentsTapped() {
        analytics.track(Event.ADD_DOCUMENTS_TAPPED, emptyMap())
        logger.info(TAG, Event.ADD_DOCUMENTS_TAPPED, tc = flowTrace.toLog())
    }

    /**
     * Automatic logging was tapped by an owner who does not have it.
     *
     * The paywall's own `shown` event says a paywall appeared; this says what the owner was
     * reaching for when it did. Without it, the card that sells automatic logging and the card
     * that sells anything else are indistinguishable in the funnel.
     */
    fun autoDetectPaywalled() {
        analytics.track(Event.AUTO_DETECT_PAYWALLED, emptyMap())
        logger.info(TAG, Event.AUTO_DETECT_PAYWALLED, tc = flowTrace.toLog())
    }

    /** "Add your car" from the no-car state — setup never finished, and this is the way back. */
    fun addCarTapped() {
        analytics.track(Event.ADD_CAR_TAPPED, emptyMap())
        logger.info(TAG, Event.ADD_CAR_TAPPED, tc = flowTrace.toLog())
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
        const val TAG = "HOME"
        const val FLOW = "home"
        const val UNKNOWN = "Unknown"
    }

    /*
     * The feature's observability taxonomy. These names are what a dashboard queries, so
     * they are shipped contracts: reuse one rather than inventing a synonym, and do not
     * rename one without accepting that its history stops there.
     */

    /** Analytics event names. Every one is declared in `dashboardAnalyticsEvents`. */
    object Event {
        const val OPENED = "home_opened"
        const val BREAKDOWN_OPENED = "home_breakdown_opened"
        const val ATTENTION_TAPPED = "home_attention_tapped"
        const val RECENT_OPENED = "home_recent_opened"
        const val TIMELINE_OPENED = "home_timeline_opened"
        const val SCAN_BILL_TAPPED = "home_scan_bill_tapped"
        const val ADD_DOCUMENTS_TAPPED = "home_add_documents_tapped"
        const val ADD_CAR_TAPPED = "home_add_car_tapped"
        const val AUTO_DETECT_PAYWALLED = "home_auto_detect_paywalled"
        const val READ_FAILED = "home_read_failed"
    }

    /** Property names carried by the events above. */
    object Key {
        const val BAND = "band"
        const val IS_NEW_USER = "is_new_user"
        const val HAS_ATTENTION = "has_attention"
        const val SETUP_DONE = "setup_done"
        const val KIND = "kind"
        const val FROM_CHECKLIST = "from_checklist"
        const val REASON = "reason"
    }
}
