package com.hopcape.odo.feature.billcheck.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlin.coroutines.CoroutineContext
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * All observability for the bill check, behind intent-named methods — so the ViewModels read
 * as the feature's logic rather than as a wall of port calls.
 *
 * **Only non-PII context is emitted.** Never a line name, a workshop, a plate or a rupee
 * figure: a bill is a record of what someone paid and where. What is worth counting is the
 * shape of the answer — how many lines were flagged, whether the wall was hit, and whether
 * the owner went on to ask where a band came from.
 *
 * Every method is fire-and-forget by contract: nothing returns a decision.
 */
internal class BillCheckTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    private val tracer: PerformanceTracer,
    private val ids: IdGenerator,
) {
    private val flowTraceId: String = "${FLOW}_${ids.newId()}"
    private val flowTrace = PerfTrace(flowId = FLOW, traceId = flowTraceId)

    /** The child-trace context to `launch(...)` an async op under. */
    fun op(name: String): CoroutineContext = flowTrace.withNewTrace("${name}_${ids.newId()}")

    /** Opens the span for reading a bill. The read is a network call with a screen waiting. */
    fun readStarted(): Span = tracer.startSpan(Trace.READ, flowTraceId)

    fun readEnded(span: Span) = tracer.endSpan(span)

    /**
     * The result rendered. [flagged] out of [lines] is the product outcome: a check that
     * flags nothing is either a fair bill or a broken lookup, and only the rate across many
     * checks tells those apart. [locked] segments it, because a masked result is a different
     * screen with a different job.
     */
    fun resultShown(flagged: Int, lines: Int, locked: Boolean) {
        val fields = mapOf(Key.FLAGGED to flagged, Key.LINES to lines, Key.LOCKED to locked)
        analytics.track(Event.RESULT_SHOWN, fields)
        logger.info(TAG, Event.RESULT_SHOWN, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * The read failed. The type, never the message — a failure's message can carry the bill
     * it choked on.
     */
    fun readFailed(error: Any) {
        logger.error(
            TAG,
            Event.READ_FAILED,
            tc = flowTrace.toLog(),
            fields = mapOf(Key.ERROR to (error::class.simpleName ?: UNKNOWN)),
        )
    }

    fun shareClicked() {
        analytics.track(Event.SHARE_CLICKED)
        logger.info(TAG, Event.SHARE_CLICKED, tc = flowTrace.toLog())
    }

    /**
     * The owner asked where a band came from. Worth counting on its own: it is the closest
     * thing to a trust signal this feature produces.
     */
    fun basisOpened() {
        analytics.track(Event.BASIS_OPENED)
        logger.info(TAG, Event.BASIS_OPENED, tc = flowTrace.toLog())
    }

    /** The wall was reached, which is where the offer is made. */
    fun offersOpened() {
        analytics.track(Event.OFFERS_OPENED)
        logger.info(TAG, Event.OFFERS_OPENED, tc = flowTrace.toLog())
    }

    /** The "add your last bill" nudge was taken. Bills scanned per month is the North Star. */
    fun addLastBillClicked() {
        analytics.track(Event.ADD_LAST_BILL_CLICKED)
        logger.info(TAG, Event.ADD_LAST_BILL_CLICKED, tc = flowTrace.toLog())
    }

    /** Play requires the report action; whether anyone uses it is worth knowing. */
    fun wrongPriceReported() {
        analytics.track(Event.WRONG_PRICE_REPORTED)
        logger.info(TAG, Event.WRONG_PRICE_REPORTED, tc = flowTrace.toLog())
    }

    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    private companion object {
        const val TAG = "BILLCHECK"
        const val FLOW = "billcheck"

        /** Stands in for a failure whose class has no name, so a field is never missing. */
        const val UNKNOWN = "Unknown"
    }

    /*
     * The feature's observability taxonomy — the single source of truth for every event,
     * span and field name. These names are what a dashboard queries, so treat them as
     * shipped contracts.
     */

    object Event {
        const val RESULT_SHOWN = "billcheck_result_shown"
        const val READ_FAILED = "billcheck_read_failed"
        const val SHARE_CLICKED = "billcheck_share_clicked"
        const val BASIS_OPENED = "billcheck_basis_opened"
        const val OFFERS_OPENED = "billcheck_offers_opened"
        const val ADD_LAST_BILL_CLICKED = "billcheck_add_last_bill_clicked"
        const val WRONG_PRICE_REPORTED = "billcheck_wrong_price_reported"
    }

    object Trace {
        const val READ = "billcheck_read"
    }

    object Key {
        const val FLAGGED = "flagged"
        const val LINES = "lines"
        const val LOCKED = "locked"
        const val ERROR = "error"
    }
}
