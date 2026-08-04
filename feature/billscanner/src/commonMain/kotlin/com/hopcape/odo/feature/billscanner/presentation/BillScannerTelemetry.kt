package com.hopcape.odo.feature.billscanner.presentation

import arrow.core.Either
import arrow.core.EitherNel
import arrow.core.NonEmptyList
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.scan.model.ExtractedBill
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import com.hopcape.performance.api.currentTraceContext
import kotlin.coroutines.CoroutineContext
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * All observability for the bill scanner, behind intent-named methods, so the ViewModels read
 * as their screens' logic instead of a wall of logger, analytics and tracer calls.
 *
 * This feature is the app's North Star metric — bills scanned per month (PRD) — so the funnel
 * here is the one the dashboard is built on: the scanner opened, the permission answered, a
 * photo taken, an extraction returned with its confidence, and a bill saved. The drop-off
 * between any two of those is what tells us which step is broken.
 *
 * **No PII.** Counts, types, confidence numbers, booleans and error *type* names only — never
 * a workshop name, an amount, an odometer reading, a UPI address or a photo. A payment is
 * reported by its transaction reference and never by what it was for.
 *
 * Every method is fire-and-forget: nothing returns a decision, and the wrapping methods hand
 * back their block's result untouched, so instrumentation cannot change what a screen does.
 */
internal class BillScannerTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    private val tracer: PerformanceTracer,
    private val ids: IdGenerator,
) {

    private val flowTraceId: String = "${FLOW}_${ids.newId()}"
    private val flowTrace = PerfTrace(flowId = FLOW, traceId = flowTraceId)

    /** The child-trace context to `launch(...)` an async op under. */
    fun op(name: String): CoroutineContext = flowTrace.withNewTrace("${name}_${ids.newId()}")

    /* ------------------------------ Permission ------------------------------ */

    /** How the owner answered. [status] is the permission state that followed. */
    fun cameraPermissionAnswered(status: String) {
        val fields = mapOf(Key.STATUS to status)
        analytics.track(Event.CAMERA_PERMISSION_ANSWERED, fields)
        logger.info(TAG, Event.CAMERA_PERMISSION_ANSWERED, tc = flowTrace.toLog(), fields = fields)
    }

    /** The owner declined and stayed on the scan screen with the nudge. */
    fun cameraDeclined() {
        analytics.track(Event.CAMERA_DECLINED)
        logger.info(TAG, Event.CAMERA_DECLINED, tc = flowTrace.toLog())
    }

    /**
     * A read the screen depends on failed — the owner's remaining scans, today.
     *
     * Worth its own event because the failure is otherwise invisible twice over: the screen
     * simply shows one fewer thing, and an unhandled failure inside a `launch` takes the
     * ViewModel's scope down with it.
     */
    fun readFailed(source: String, cause: Throwable) {
        val fields = mapOf(Key.SOURCE to source, Key.REASON to (cause::class.simpleName ?: UNKNOWN))
        analytics.track(Event.READ_FAILED, fields)
        logger.error(TAG, Event.READ_FAILED, tc = flowTrace.toLog(), fields = fields)
    }

    /* ------------------------------ Capture ------------------------------ */

    /** The scanner was opened, on [target]. */
    fun scannerOpened(target: String) {
        val fields = mapOf(Key.TARGET to target)
        analytics.track(Event.SCANNER_OPENED, fields)
        logger.debug(TAG, Event.SCANNER_OPENED, tc = flowTrace.toLog(), fields = fields)
    }

    /** The owner switched what they were scanning mid-flow. */
    fun targetSwitched(target: String) {
        analytics.track(Event.TARGET_SWITCHED, mapOf(Key.TARGET to target))
    }

    /** A photo was taken. The step every later number is a fraction of. */
    fun photoCaptured(target: String) {
        val fields = mapOf(Key.TARGET to target)
        analytics.track(Event.PHOTO_CAPTURED, fields)
        logger.info(TAG, Event.PHOTO_CAPTURED, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * The camera itself failed. Worth an error rather than an event: it means the owner is
     * looking at a viewfinder that cannot take a picture, and nothing else reports it.
     */
    fun cameraFailed(failure: String) {
        val fields = mapOf(Key.REASON to failure)
        analytics.track(Event.CAMERA_FAILED, fields)
        logger.error(TAG, Event.CAMERA_FAILED, tc = flowTrace.toLog(), fields = fields)
    }

    /* ------------------------------ Extraction ------------------------------ */

    /**
     * Times an extraction and records what came back.
     *
     * The confidence and the manual-review fallback are tracked as the feature's *product*
     * outcome, not as diagnostics: "how often does the scanner read a bill well enough to
     * trust" is the question this feature lives or dies on.
     */
    suspend fun billExtraction(
        read: suspend () -> Either<DomainError, ExtractedBill>,
    ): Either<DomainError, ExtractedBill> = traced(Trace.EXTRACT_BILL) { span ->
        val result = read()
        result.fold(
            ifLeft = { error ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                val fields = mapOf(Key.ERRORS to error.typeName())
                analytics.track(Event.EXTRACTION_FAILED, fields)
                logger.error(TAG, Event.EXTRACTION_FAILED, tc = currentTraceContext().toLog(), fields = fields)
            },
            ifRight = { bill ->
                val fields = mapOf(
                    Key.CONFIDENCE to bill.confidence.percent,
                    Key.BILL_TYPE to bill.billType.name,
                    Key.LINE_ITEM_COUNT to bill.lineItems.size,
                    Key.MANUAL_REVIEW to bill.requiresManualReview,
                    Key.HAS_ODOMETER to (bill.odometerKm != null),
                )
                span.setAttribute(Key.CONFIDENCE, bill.confidence.percent)
                analytics.track(Event.BILL_EXTRACTED, fields)
                logger.info(TAG, Event.BILL_EXTRACTED, tc = currentTraceContext().toLog(), fields = fields)
            },
        )
        result
    }

    /** The document counterpart. [hasExpiry] is the only field that decides whether it is useful. */
    suspend fun <T> documentExtraction(
        read: suspend () -> Either<DomainError, T>,
        hasExpiry: (T) -> Boolean,
    ): Either<DomainError, T> = traced(Trace.EXTRACT_DOCUMENT) { span ->
        val result = read()
        result.fold(
            ifLeft = { error ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                val fields = mapOf(Key.ERRORS to error.typeName())
                analytics.track(Event.EXTRACTION_FAILED, fields)
                logger.error(TAG, Event.EXTRACTION_FAILED, tc = currentTraceContext().toLog(), fields = fields)
            },
            ifRight = { extracted ->
                val fields = mapOf(Key.HAS_EXPIRY to hasExpiry(extracted))
                analytics.track(Event.DOCUMENT_EXTRACTED, fields)
                logger.info(TAG, Event.DOCUMENT_EXTRACTED, tc = currentTraceContext().toLog(), fields = fields)
            },
        )
        result
    }

    /* ------------------------------ Saving ------------------------------ */

    /** Times the write of a reviewed bill — the North Star event. */
    suspend fun <T> billSave(
        edited: Boolean,
        write: suspend () -> EitherNel<DomainError, T>,
    ): EitherNel<DomainError, T> = traced(Trace.SAVE_BILL, Key.EDITED to edited) { span ->
        val result = write()
        result.fold(
            ifLeft = { errors ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                val fields = mapOf(Key.ERRORS to errors.typeNames())
                analytics.track(Event.SAVE_FAILED, fields)
                logger.error(TAG, Event.SAVE_FAILED, tc = currentTraceContext().toLog(), fields = fields)
            },
            ifRight = {
                val fields = mapOf(Key.EDITED to edited)
                analytics.track(Event.BILL_SAVED, fields)
                logger.info(TAG, Event.BILL_SAVED, tc = currentTraceContext().toLog(), fields = fields)
            },
        )
        result
    }

    /** Times the write of a scanned document into the vault. */
    suspend fun <T> documentSave(
        type: String,
        write: suspend () -> EitherNel<DomainError, T>,
    ): EitherNel<DomainError, T> = traced(Trace.SAVE_DOCUMENT, Key.TYPE to type) { span ->
        val result = write()
        result.fold(
            ifLeft = { errors ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                val fields = mapOf(Key.TYPE to type, Key.ERRORS to errors.typeNames())
                analytics.track(Event.SAVE_FAILED, fields)
                logger.error(TAG, Event.SAVE_FAILED, tc = currentTraceContext().toLog(), fields = fields)
            },
            ifRight = {
                analytics.track(Event.DOCUMENT_SAVED, mapOf(Key.TYPE to type))
                logger.info(TAG, Event.DOCUMENT_SAVED, tc = currentTraceContext().toLog(), fields = mapOf(Key.TYPE to type))
            },
        )
        result
    }

    /* ------------------------------ Payments ------------------------------ */

    /** A payment QR was read and understood. */
    fun paymentQrParsed(hasAmount: Boolean) {
        val fields = mapOf(Key.QR_HAS_AMOUNT to hasAmount)
        analytics.track(Event.PAYMENT_QR_PARSED, fields)
        logger.info(TAG, Event.PAYMENT_QR_PARSED, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * A code was read but is not one Odo can pay.
     *
     * Counted because it is demand, not noise: most of these will be EMVCo/Bharat QRs, which
     * are deliberately refused today, and how often owners point at one is what decides
     * whether that grammar is worth supporting.
     */
    fun paymentQrRejected(reason: String) {
        val fields = mapOf(Key.ERRORS to reason)
        analytics.track(Event.PAYMENT_QR_REJECTED, fields)
        logger.info(TAG, Event.PAYMENT_QR_REJECTED, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * The owner was handed off to a UPI app.
     *
     * Every payment state transition is logged with its reference and never with the amount
     * on its own — a figure with nothing to tie it to is untraceable, and the two together
     * are what makes a disputed fill checkable.
     */
    fun paymentInitiated() {
        analytics.track(Event.PAYMENT_INITIATED)
        logger.info(TAG, Event.PAYMENT_INITIATED, tc = flowTrace.toLog())
    }

    /** How the payment ended, by status and (when there is one) its bank reference. */
    fun paymentSettled(status: String, reference: String?) {
        val fields = buildMap {
            put(Key.STATUS, status)
            reference?.let { put(Key.TXN_REF, it) }
        }
        analytics.track(Event.PAYMENT_SETTLED, fields)
        logger.info(TAG, Event.PAYMENT_SETTLED, tc = flowTrace.toLog(), fields = fields)
    }

    /** Times the write of a fuel fill. Only ever called after a confirmed payment. */
    suspend fun <T> fillSave(
        write: suspend () -> EitherNel<DomainError, T>,
    ): EitherNel<DomainError, T> = traced(Trace.SAVE_FILL) { span ->
        val result = write()
        result.fold(
            ifLeft = { errors ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                val fields = mapOf(Key.ERRORS to errors.typeNames())
                analytics.track(Event.FILL_SAVE_FAILED, fields)
                // A fill that fails to save after the money left is the worst state this
                // feature can reach: the owner has paid and Odo has no record of it.
                logger.error(TAG, Event.FILL_SAVE_FAILED, tc = currentTraceContext().toLog(), fields = fields)
            },
            ifRight = {
                analytics.track(Event.FILL_SAVED)
                logger.info(TAG, Event.FILL_SAVED, tc = currentTraceContext().toLog())
            },
        )
        result
    }

    /* ------------------------------ Plumbing ------------------------------ */

    /**
     * Open a span on the calling op's trace, run [block], and close the span whichever way it
     * goes. A cancelled or throwing block still ends its span; an unclosed one reads on a
     * dashboard as an operation that never happened.
     */
    private suspend fun <T> traced(
        name: String,
        vararg attributes: Pair<String, Any?>,
        block: suspend (Span) -> T,
    ): T {
        val trace = currentTraceContext()
        val span = tracer.startSpan(name, trace.traceId ?: flowTraceId)
        attributes.forEach { (key, value) -> span.setAttribute(key, value) }
        return try {
            block(span)
        } finally {
            tracer.endSpan(span)
        }
    }

    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    private fun DomainError.typeName(): String = this::class.simpleName ?: UNKNOWN

    private fun NonEmptyList<DomainError>.typeNames(): String = joinToString(",") { it.typeName() }

    private companion object {
        const val TAG = "BILLSCANNER"
        const val FLOW = "billscanner"
        const val UNKNOWN = "Unknown"
    }

    /*
     * The feature's observability taxonomy. These names are what a dashboard queries, so they
     * are shipped contracts: reuse one rather than inventing a synonym, and do not rename one
     * without knowing you are breaking its history.
     */

    /** Analytics event names. */
    object Event {
        const val CAMERA_PERMISSION_ANSWERED = "scanner_camera_permission_answered"
        const val CAMERA_DECLINED = "scanner_camera_declined"
        const val READ_FAILED = "scanner_read_failed"
        const val SCANNER_OPENED = "scanner_opened"
        const val TARGET_SWITCHED = "scanner_target_switched"
        const val PHOTO_CAPTURED = "scanner_photo_captured"
        const val CAMERA_FAILED = "scanner_camera_failed"
        const val BILL_EXTRACTED = "scanner_bill_extracted"
        const val DOCUMENT_EXTRACTED = "scanner_document_extracted"
        const val EXTRACTION_FAILED = "scanner_extraction_failed"
        const val BILL_SAVED = "scanner_bill_saved"
        const val DOCUMENT_SAVED = "scanner_document_saved"
        const val SAVE_FAILED = "scanner_save_failed"
        const val PAYMENT_QR_PARSED = "scanner_payment_qr_parsed"
        const val PAYMENT_QR_REJECTED = "scanner_payment_qr_rejected"
        const val PAYMENT_INITIATED = "scanner_payment_initiated"
        const val PAYMENT_SETTLED = "scanner_payment_settled"
        const val FILL_SAVED = "scanner_fill_saved"
        const val FILL_SAVE_FAILED = "scanner_fill_save_failed"
    }

    /** Span names for the feature's async operations. */
    object Trace {
        const val EXTRACT_BILL = "scanner_extract_bill"
        const val EXTRACT_DOCUMENT = "scanner_extract_document"
        const val SAVE_BILL = "scanner_save_bill"
        const val SAVE_DOCUMENT = "scanner_save_document"
        const val SAVE_FILL = "scanner_save_fill"
    }

    /** Property names carried by the events and spans above. */
    object Key {
        const val TARGET = "target"
        const val STATUS = "status"
        const val REASON = "reason"
        const val SOURCE = "source"
        const val ERRORS = "errors"
        const val OUTCOME = "outcome"
        const val CONFIDENCE = "confidence"
        const val BILL_TYPE = "bill_type"
        const val LINE_ITEM_COUNT = "line_item_count"
        const val MANUAL_REVIEW = "manual_review"
        const val HAS_ODOMETER = "has_odometer"
        const val HAS_EXPIRY = "has_expiry"
        const val EDITED = "edited"
        const val TYPE = "type"
        const val QR_HAS_AMOUNT = "qr_has_amount"
        const val TXN_REF = "txn_ref"
    }

    /** Values for [Key.SOURCE] when it names what was being read. */
    object Read {
        const val ALLOWANCE = "allowance"
    }

    /** Values for [Key.OUTCOME]. */
    object Outcome {
        const val FAILED = "failed"
    }
}
