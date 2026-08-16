package com.hopcape.odo.infrastructure.ai.observability

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.Logger
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.currentTraceContext
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * Observability for the AI adapters, behind one intent-named surface.
 *
 * The Bill Scanner is the make-or-break feature, so its failure modes must be visible:
 * every extraction is spanned, and a recogniser that throws is a non-fatal. What is *not*
 * here is product analytics — `BillScannerTelemetry` already counts extraction outcomes at
 * the feature layer, and a second event for the same scan would make the dashboard wrong.
 *
 * **Never log PII.** Targets, confidence numbers and error type names only — never a
 * storage path or anything read off the bill.
 */
internal class AiTelemetry(
    private val logger: Logger,
    private val tracer: PerformanceTracer,
    private val crash: CrashRecorder,
) {

    /** Run [block] inside a span named `ai.extract`, on the caller's trace. */
    suspend fun <T> span(target: String, block: suspend () -> T): T {
        val trace = currentTraceContext()
        val span = tracer.startSpan(name = "$TAG.$EXTRACT", traceId = trace.traceId ?: UNTRACED)
        return try {
            block()
        } finally {
            tracer.endSpan(span)
            logger.debug(
                TAG,
                "$EXTRACT.done",
                tc = trace.toLog(),
                fields = mapOf(Key.TARGET to target),
            )
        }
    }

    /**
     * An extraction came back. The confidence and the review flag are the product outcome
     * the PRD grades this feature on, logged where the call was made so a bad week of
     * extractions is diagnosable from one place.
     */
    suspend fun extracted(target: String, confidencePercent: Int, manualReview: Boolean, engine: String) {
        logger.info(
            TAG,
            "$EXTRACT.result",
            tc = currentTraceContext().toLog(),
            fields = mapOf(
                Key.TARGET to target,
                Key.CONFIDENCE to confidencePercent,
                Key.MANUAL_REVIEW to manualReview,
                Key.ENGINE to engine,
            ),
        )
    }

    /**
     * The recogniser itself failed on a decodable image. Broken pipeline, not a bad
     * photo, so it is a non-fatal with a stack trace, not a log line that scrolls away.
     */
    suspend fun malformed(target: String, throwable: Throwable) {
        crash.recordNonFatal(throwable, mapOf(Key.TARGET to target))
        logger.error(
            TAG,
            "$EXTRACT.malformed",
            tc = currentTraceContext().toLog(),
            fields = mapOf(Key.TARGET to target, Key.ERROR to throwable::class.simpleName),
        )
    }

    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    /** Field keys — kept here so a dashboard query never breaks on a renamed literal. */
    internal object Key {
        const val TARGET = "target"
        const val CONFIDENCE = "confidence"
        const val ENGINE = "engine"
        const val MANUAL_REVIEW = "manual_review"
        const val ERROR = "error"
    }

    internal companion object {
        const val TAG = "ai"
        const val EXTRACT = "extract"
        const val UNTRACED = "untraced"

        /** Target — which kind of paper was read. */
        const val BILL = "bill"
        const val DOCUMENT = "document"

        /**
         * Not paper at all: the lit display on a fuel pump.
         *
         * Worth its own target because it is the reader whose accuracy is least certain —
         * seven-segment digits are not what the recogniser was trained on — and the only way
         * to know whether it is working is to watch its results separately from the others.
         */
        const val PUMP = "pump"

        /** Engine — which pipeline produced the result. */
        const val ENGINE_MLKIT = "mlkit"
    }
}
