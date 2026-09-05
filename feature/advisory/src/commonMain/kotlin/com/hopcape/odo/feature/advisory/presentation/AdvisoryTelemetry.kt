package com.hopcape.odo.feature.advisory.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.CoroutineContext
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * All observability for the advisory screens, behind intent-named methods — so the
 * ViewModels read as the feature's logic rather than as a wall of port calls.
 *
 * **Only non-PII context is emitted.** Whether a record exists is a fact about the app's
 * data; what the car is worth is a fact about the owner's property, and no rupee figure,
 * plate, reading or city ever leaves here. What is worth counting is the funnel: the value
 * screen exists to turn "my car is worth X" into a scanned bill, and only the scan rate
 * says whether it does.
 *
 * Every method is fire-and-forget by contract: nothing returns a decision, and the
 * wrapping methods hand back their block's result untouched.
 */
internal class AdvisoryTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    private val tracer: PerformanceTracer,
    private val ids: IdGenerator,
) {
    private val flowTraceId: String = "${FLOW}_${ids.newId()}"
    private val flowTrace = PerfTrace(flowId = FLOW, traceId = flowTraceId)

    /** The child-trace context to `launch(...)` an async op under. */
    fun op(name: String): CoroutineContext = flowTrace.withNewTrace("${name}_${ids.newId()}")

    /**
     * The screen opened. [hasRecord] is the segment that matters: an owner with no record is
     * the one the screen is written for, and their scan rate is what it is judged on.
     */
    fun valueShown(hasRecord: Boolean) {
        val fields = mapOf(Key.HAS_RECORD to hasRecord)
        analytics.track(Event.VALUE_SHOWN, fields)
        logger.info(TAG, Event.VALUE_SHOWN, tc = flowTrace.toLog(), fields = fields)
    }

    /** Bills scanned per month is the North Star, so every launch point is worth naming. */
    fun scanClicked() {
        analytics.track(Event.SCAN_CLICKED)
        logger.info(TAG, Event.SCAN_CLICKED, tc = flowTrace.toLog())
    }

    fun shareClicked() {
        analytics.track(Event.SHARE_CLICKED)
        logger.info(TAG, Event.SHARE_CLICKED, tc = flowTrace.toLog())
    }

    /**
     * Times how long [source] takes to produce its **first** value, then gets out of the way.
     *
     * The estimate is a table lookup and four multiplications, but it sits behind three
     * repository reads and a synced city catalog — and a screen whose one number never
     * arrives looks identical to a screen that crashed.
     *
     * The span closes on that first value rather than when collection ends, because
     * collection never ends: the source is a live stream kept open for as long as the screen
     * is. Timing the whole collection reported the owner's dwell time as the load time, so a
     * healthy screen read as minutes slow.
     */
    fun <T> timeToFirstValue(source: Flow<T>): Flow<T> = flow {
        val span = tracer.startSpan(Trace.LOAD, flowTraceId)
        var open = true
        try {
            source.collect { value ->
                if (open) {
                    open = false
                    tracer.endSpan(span)
                }
                emit(value)
            }
        } finally {
            // The screen was left, or the read failed, before anything arrived. Closing it
            // here is what stops an unclosed span reading as an op that never happened.
            if (open) tracer.endSpan(span)
        }
    }

    /** The car read came back empty. Setup writes a car, so an empty answer is worth a line. */
    fun noCar() {
        logger.warn(TAG, Event.NO_CAR, tc = flowTrace.toLog())
    }

    /**
     * The city catalog could not be read, so the estimate fell back to the middle tier.
     *
     * Worth a line because it is otherwise invisible: the screen still renders a plausible
     * figure, just one built against the wrong city. Logged rather than recorded as a
     * non-fatal — offline is a normal reason for this, not a broken build.
     */
    fun cityCatalogUnavailable(cause: Throwable) {
        logger.warn(
            TAG,
            Event.CITY_CATALOG_UNAVAILABLE,
            tc = flowTrace.toLog(),
            // The type, not the message: a read failure's message can carry the row it
            // choked on, and that row is the owner's profile.
            fields = mapOf(Key.CAUSE to (cause::class.simpleName ?: UNKNOWN)),
        )
    }

    /**
     * The catalog loaded but does not list the owner's city, which means the synced catalog
     * and what the profile stores have drifted apart. [size] says whether the catalog is
     * merely empty or genuinely missing that one city. The name is never emitted — it is
     * where the owner lives.
     */
    fun cityNotListed(size: Int) {
        logger.warn(
            TAG,
            Event.CITY_NOT_LISTED,
            tc = flowTrace.toLog(),
            fields = mapOf(Key.COUNT to size),
        )
    }

    /**
     * The checklist opened, and how much of it Odo could actually answer.
     *
     * [priced] beside [due] is the point: the reference tables are being filled by hand, and
     * the gap between "five jobs due" and "three of them priced" is what says whether the
     * cost line is carrying its weight. [entry] is which of the three doors was used — the
     * conditional Home card cannot be judged against the two permanent entries otherwise.
     */
    fun checklistShown(due: Int, priced: Int, entry: String) {
        val fields = mapOf(Key.DUE to due, Key.PRICED to priced, Key.ENTRY to entry)
        analytics.track(Event.CHECKLIST_SHOWN, fields)
        logger.info(TAG, Event.CHECKLIST_SHOWN, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * The schedule could not be read, so the list is missing rather than short.
     *
     * The two look identical on screen. Without this line a reference-data outage reads as
     * a car with nothing due, which is the answer nobody would investigate.
     */
    fun scheduleUnavailable() {
        logger.warn(TAG, Event.CHECKLIST_NO_SCHEDULE, tc = flowTrace.toLog())
    }

    /** The read failed outright — usually no car, which setup is supposed to have written. */
    fun checklistUnavailable(error: String?) {
        logger.warn(
            TAG,
            Event.CHECKLIST_UNAVAILABLE,
            tc = flowTrace.toLog(),
            // The error's type name, never the input that produced it.
            fields = mapOf(Key.CAUSE to (error ?: UNKNOWN)),
        )
    }

    fun checklistSaveClicked() {
        analytics.track(Event.CHECKLIST_SAVE_CLICKED)
        logger.info(TAG, Event.CHECKLIST_SAVE_CLICKED, tc = flowTrace.toLog())
    }

    fun checklistSaveFailed(reason: String) {
        logger.warn(
            TAG,
            Event.CHECKLIST_SAVE_FAILED,
            tc = flowTrace.toLog(),
            fields = mapOf(Key.CAUSE to reason),
        )
    }

    /**
     * The load, which is a schedule read plus one band lookup per due job.
     *
     * Timed because it is the slowest thing on the screen and the owner is standing at a
     * counter waiting for it. Started and ended by the caller rather than wrapped, so the
     * span covers the whole read including the bands.
     */
    fun checklistLoadStarted(): Span = tracer.startSpan(Trace.CHECKLIST_LOAD, flowTraceId)

    fun checklistLoadEnded(span: Span) = tracer.endSpan(span)

    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    private companion object {
        const val TAG = "ADVISORY"
        const val FLOW = "advisory"

        /** Stands in for a failure whose class has no name, so a field is never missing. */
        const val UNKNOWN = "Unknown"
    }

    /*
     * The feature's observability taxonomy — the single source of truth for every event,
     * span and field name. These names are what a dashboard queries, so treat them as
     * shipped contracts.
     */

    object Event {
        const val VALUE_SHOWN = "advisory_value_shown"
        const val SCAN_CLICKED = "advisory_value_scan_clicked"
        const val SHARE_CLICKED = "advisory_value_share_clicked"
        const val NO_CAR = "advisory_value_no_car"
        const val CITY_CATALOG_UNAVAILABLE = "advisory_city_catalog_unavailable"
        const val CITY_NOT_LISTED = "advisory_city_not_listed"
        const val CHECKLIST_SHOWN = "advisory_checklist_shown"
        const val CHECKLIST_NO_SCHEDULE = "advisory_checklist_no_schedule"
        const val CHECKLIST_UNAVAILABLE = "advisory_checklist_unavailable"
        const val CHECKLIST_SAVE_CLICKED = "advisory_checklist_save_clicked"
        const val CHECKLIST_SAVE_FAILED = "advisory_checklist_save_failed"
    }

    object Trace {
        const val LOAD = "advisory_value_load"
        const val CHECKLIST_LOAD = "advisory_checklist_load"
    }

    object Key {
        const val HAS_RECORD = "has_record"
        const val COUNT = "count"
        const val CAUSE = "cause"
        const val DUE = "due"
        const val PRICED = "priced"
        const val ENTRY = "entry"
    }
}
