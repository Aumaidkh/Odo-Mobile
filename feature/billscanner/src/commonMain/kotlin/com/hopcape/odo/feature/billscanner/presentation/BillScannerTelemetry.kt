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
 * a workshop name, an amount, an odometer reading or a photo.
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

    /** The owner declined, which ends the scan: there is no viewfinder without the camera. */
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
     * The gallery was opened, and what came of it.
     *
     * A funnel of its own, because picking a picture is a different journey from pointing
     * the camera at something: an owner who imports a screenshot of a bill never sees the
     * viewfinder, and how often that happens decides whether the camera is even the primary
     * path. The picked file's reference is never recorded — it names a file on the device.
     */
    fun galleryOpened(target: String) {
        val fields = mapOf(Key.TARGET to target)
        analytics.track(Event.GALLERY_OPENED, fields)
        logger.info(TAG, Event.GALLERY_OPENED, tc = flowTrace.toLog(), fields = fields)
    }

    fun galleryImported(target: String) {
        analytics.track(Event.GALLERY_IMPORTED, mapOf(Key.TARGET to target))
    }

    /** The picked file could not be copied into app storage — the picture never arrives. */
    fun galleryImportFailed(target: String) {
        val fields = mapOf(Key.TARGET to target)
        analytics.track(Event.GALLERY_IMPORT_FAILED, fields)
        logger.warn(TAG, Event.GALLERY_IMPORT_FAILED, tc = flowTrace.toLog(), fields = fields)
    }

    /** A picture was imported to pay from, and held no code. Expected often enough to count. */
    fun galleryQrMissing() {
        analytics.track(Event.GALLERY_QR_MISSING, emptyMap())
        logger.info(TAG, Event.GALLERY_QR_MISSING, tc = flowTrace.toLog())
    }

    /** The owner pinned or released the detected outline. A log — a UI nicety, not a funnel step. */
    fun edgeLockToggled(locked: Boolean) {
        logger.info(
            TAG,
            Event.EDGE_LOCK_TOGGLED,
            tc = flowTrace.toLog(),
            fields = mapOf(Key.APPLIED to locked),
        )
    }

    /**
     * Whether the capture was auto-cropped to the detected outline. A log rather than an
     * analytics event — it qualifies the capture, and PHOTO_CAPTURED already counts it.
     */
    fun photoCropped(target: String, applied: Boolean) {
        logger.info(
            TAG,
            Event.PHOTO_CROPPED,
            tc = flowTrace.toLog(),
            fields = mapOf(Key.TARGET to target, Key.APPLIED to applied),
        )
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

    /**
     * The pump-display counterpart.
     *
     * [readCount] and [crossChecked] are here rather than a confidence percent because that
     * is the only honest measure this reader has: how many of the three numbers came back,
     * and whether they agreed with each other. It is also the reader whose accuracy is least
     * certain — seven-segment digits are not what the recogniser was trained on — so these
     * two numbers are what say whether the mode is worth keeping.
     */
    suspend fun <T> pumpExtraction(
        read: suspend () -> Either<DomainError, T>,
        readCount: (T) -> Int,
        crossChecked: (T) -> Boolean,
    ): Either<DomainError, T> = traced(Trace.EXTRACT_PUMP) { span ->
        val result = read()
        result.fold(
            ifLeft = { error ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                val fields = mapOf(Key.ERRORS to error.typeName())
                analytics.track(Event.EXTRACTION_FAILED, fields)
                logger.error(TAG, Event.EXTRACTION_FAILED, tc = currentTraceContext().toLog(), fields = fields)
            },
            ifRight = { reading ->
                val fields = mapOf(
                    Key.FIELDS_READ to readCount(reading),
                    Key.CROSS_CHECKED to crossChecked(reading),
                )
                analytics.track(Event.PUMP_EXTRACTED, fields)
                logger.info(TAG, Event.PUMP_EXTRACTED, tc = currentTraceContext().toLog(), fields = fields)
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
        origin: String,
        write: suspend () -> EitherNel<DomainError, T>,
    ): EitherNel<DomainError, T> = traced(Trace.SAVE_DOCUMENT, Key.TYPE to type) { span ->
        val result = write()
        val fields = mapOf(Key.TYPE to type, Key.ORIGIN to origin)
        result.fold(
            ifLeft = { errors ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                val failure = fields + (Key.ERRORS to errors.typeNames())
                analytics.track(Event.SAVE_FAILED, failure)
                logger.error(TAG, Event.SAVE_FAILED, tc = currentTraceContext().toLog(), fields = failure)
            },
            ifRight = {
                analytics.track(Event.DOCUMENT_SAVED, fields)
                logger.info(TAG, Event.DOCUMENT_SAVED, tc = currentTraceContext().toLog(), fields = fields)
            },
        )
        result
    }

    /* ------------------------------ Payments ------------------------------ */






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

    /**
     * The free-scan pill was tapped.
     *
     * A gate-hit: it counts owners who noticed the limit, whether or not they go on to
     * subscribe. How often a cap is looked at is what says whether it was worth having.
     */
    fun quotaTapped(remaining: Int) {
        val fields = mapOf(Key.REMAINING to remaining)
        analytics.track(Event.QUOTA_TAPPED, fields)
        logger.info(TAG, Event.QUOTA_TAPPED, tc = flowTrace.toLog(), fields = fields)
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
        const val QUOTA_TAPPED = "scanner_quota_tapped"
        const val TARGET_SWITCHED = "scanner_target_switched"
        const val PHOTO_CAPTURED = "scanner_photo_captured"
        const val PHOTO_CROPPED = "scanner_photo_cropped"
        const val GALLERY_OPENED = "scanner_gallery_opened"
        const val GALLERY_IMPORTED = "scanner_gallery_imported"
        const val GALLERY_IMPORT_FAILED = "scanner_gallery_import_failed"
        const val GALLERY_QR_MISSING = "scanner_gallery_qr_missing"
        const val EDGE_LOCK_TOGGLED = "scanner_edge_lock_toggled"
        const val CAMERA_FAILED = "scanner_camera_failed"
        const val BILL_EXTRACTED = "scanner_bill_extracted"
        const val DOCUMENT_EXTRACTED = "scanner_document_extracted"
        const val PUMP_EXTRACTED = "scanner_pump_extracted"
        const val EXTRACTION_FAILED = "scanner_extraction_failed"
        const val BILL_SAVED = "scanner_bill_saved"
        const val DOCUMENT_SAVED = "scanner_document_saved"
        const val SAVE_FAILED = "scanner_save_failed"
    }

    /** Span names for the feature's async operations. */
    object Trace {
        const val GALLERY_IMPORT = "scanner_gallery_import"
        const val EXTRACT_BILL = "scanner_extract_bill"
        const val EXTRACT_DOCUMENT = "scanner_extract_document"
        const val EXTRACT_PUMP = "scanner_extract_pump"
        const val SAVE_BILL = "scanner_save_bill"
        const val SAVE_DOCUMENT = "scanner_save_document"
    }

    /** Property names carried by the events and spans above. */
    object Key {
        const val TARGET = "target"
        const val REMAINING = "remaining"
        const val STATUS = "status"
        const val REASON = "reason"
        const val SOURCE = "source"
        const val ERRORS = "errors"
        const val OUTCOME = "outcome"
        const val CONFIDENCE = "confidence"
        const val BILL_TYPE = "bill_type"
        const val LINE_ITEM_COUNT = "line_item_count"
        const val MANUAL_REVIEW = "manual_review"
        const val APPLIED = "applied"
        const val HAS_ODOMETER = "has_odometer"
        const val HAS_EXPIRY = "has_expiry"
        const val FIELDS_READ = "fields_read"
        const val CROSS_CHECKED = "cross_checked"
        const val EDITED = "edited"
        const val TYPE = "type"
        const val ORIGIN = "origin"
        const val QR_HAS_AMOUNT = "qr_has_amount"
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
