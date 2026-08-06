package com.hopcape.odo.feature.documentvault.presentation

import arrow.core.Either
import arrow.core.EitherNel
import arrow.core.NonEmptyList
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import com.hopcape.performance.api.currentTraceContext
import kotlin.coroutines.CoroutineContext
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * All observability for the document vault, behind intent-named methods, so the ViewModels
 * read as their screens' logic instead of a wall of logger, analytics and tracer calls.
 *
 * One class owns the three ports, the taxonomy ([Event], [Trace], [Key]) and the trace
 * plumbing:
 *
 *  - [flowTrace] is minted per instance. Registered as a Koin `factory`, so one instance
 *    covers one visit to the vault and every screen of that visit shares one flow id.
 *  - [op] returns the child-trace context to launch an async op under. The `suspend`
 *    methods read [currentTraceContext] themselves, so a span and its log lines match
 *    without the caller passing anything.
 *
 * **No PII.** Document types, counts, booleans, ids and error *type* names only — never a
 * title, a policy number, a file path or a date. A title is text the owner typed about
 * their own insurance.
 *
 * Every method is fire-and-forget: nothing here returns a decision, and the wrapping
 * methods hand back their block's result untouched, so instrumentation cannot change what
 * a screen does.
 */
internal class DocumentVaultTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    private val tracer: PerformanceTracer,
    private val ids: IdGenerator,
) {

    private val flowTraceId: String = "${FLOW}_${ids.newId()}"
    private val flowTrace = PerfTrace(flowId = FLOW, traceId = flowTraceId)

    /** The child-trace context to `launch(...)` an async op under. */
    fun op(name: String): CoroutineContext = flowTrace.withNewTrace("${name}_${ids.newId()}")

    /* ------------------------------ Vault ------------------------------ */

    /**
     * The vault was opened, with what it found: how many documents are on file, how many
     * are missing, and how many need renewing. These three numbers are the feature's
     * reason to exist, so they are what the dashboard should show.
     */
    fun vaultOpened(onFile: Int, missing: Int, needsAttention: Int) {
        val fields = mapOf(
            Key.ON_FILE_COUNT to onFile,
            Key.MISSING_COUNT to missing,
            Key.ATTENTION_COUNT to needsAttention,
        )
        analytics.track(Event.VAULT_OPENED, fields)
        logger.info(TAG, Event.VAULT_OPENED, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * A read of the local DB failed, on the screen named by [source].
     *
     * The local DB is the source of truth, so this is a broken record rather than a slow
     * network, and it is otherwise invisible twice over: the screen sits on whatever it was
     * showing, and an unhandled failure inside a `collect` takes the ViewModel's scope down.
     */
    fun readFailed(source: String, cause: Throwable) {
        val fields = mapOf(Key.SOURCE to source, Key.REASON to (cause::class.simpleName ?: UNKNOWN))
        analytics.track(Event.READ_FAILED, fields)
        logger.error(TAG, Event.READ_FAILED, tc = flowTrace.toLog(), fields = fields)
    }

    /** A document was opened from the vault. */
    fun documentOpened(type: DocumentType, verified: Boolean) {
        val fields = mapOf(Key.TYPE to type.name, Key.VERIFIED to verified)
        analytics.track(Event.DOCUMENT_OPENED, fields)
        logger.debug(TAG, Event.DOCUMENT_OPENED, tc = flowTrace.toLog(), fields = fields)
    }

    /** The detail screen was opened for a document that is no longer there. */
    fun documentMissing(id: DocumentId) {
        logger.warn(TAG, Event.DOCUMENT_MISSING, tc = flowTrace.toLog(), fields = mapOf(Key.DOCUMENT_ID to id.value))
    }

    /**
     * The stored file is gone while its row is not — a restore that brought the database
     * back without the files directory. Worth a warning: nothing else in the app notices.
     */
    fun fileMissing(id: DocumentId) {
        logger.warn(TAG, Event.FILE_MISSING, tc = flowTrace.toLog(), fields = mapOf(Key.DOCUMENT_ID to id.value))
    }

    /* ------------------------------ Add ------------------------------ */

    /** The add flow was opened, from a vault row's "Add" ([prefilled]) or the bottom bar. */
    fun addOpened(prefilled: Boolean) {
        analytics.track(Event.ADD_OPENED, mapOf(Key.PREFILLED to prefilled))
        logger.debug(TAG, Event.ADD_OPENED, tc = flowTrace.toLog(), fields = mapOf(Key.PREFILLED to prefilled))
    }

    /** Which documents owners actually add — the number that decides what to support next. */
    fun typeSelected(type: DocumentType) {
        analytics.track(Event.TYPE_SELECTED, mapOf(Key.TYPE to type.name))
    }

    /**
     * A capture method was started — the top of the add funnel, per method. What the owner
     * does next (a filed document, or nothing) is what turns this into a completion rate.
     */
    fun captureStarted(method: String, type: DocumentType) {
        analytics.track(Event.CAPTURE_STARTED, mapOf(Key.METHOD to method, Key.TYPE to type.name))
    }

    /**
     * A capture method with no implementation behind it was tapped. Counted because it is
     * demand: how often owners reach for the scanner or DigiLocker decides which is built
     * first.
     */
    fun captureUnavailable(method: String) {
        analytics.track(Event.CAPTURE_UNAVAILABLE, mapOf(Key.METHOD to method))
        logger.info(TAG, Event.CAPTURE_UNAVAILABLE, tc = flowTrace.toLog(), fields = mapOf(Key.METHOD to method))
    }

    /* ------------------------------ Async ops ------------------------------ */

    /**
     * Times the add and records the outcome. The file copy runs inside the same span,
     * because from the owner's side the wait is one thing: the document being saved.
     */
    suspend fun documentSave(
        type: DocumentType,
        write: suspend () -> EitherNel<DomainError, Document>,
    ): EitherNel<DomainError, Document> = traced(Trace.SAVE_DOCUMENT, Key.TYPE to type.name) { span ->
        val result = write()
        result.fold(
            ifLeft = { errors ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                saveFailed(type, errors)
            },
            ifRight = { document ->
                span.setAttribute(Key.DOCUMENT_ID, document.id.value)
                val fields = mapOf(
                    Key.TYPE to type.name,
                    Key.SOURCE to document.source.name,
                    Key.HAS_EXPIRY to (document.expiresOn != null),
                )
                analytics.track(Event.DOCUMENT_ADDED, fields)
                logger.info(TAG, Event.DOCUMENT_ADDED, tc = currentTraceContext().toLog(), fields = fields)
            },
        )
        result
    }

    /** Times the replace. A failed replace leaves the owner looking at the old scan. */
    suspend fun fileReplace(
        id: DocumentId,
        write: suspend () -> Either<DomainError, Document>,
    ): Either<DomainError, Document> = traced(Trace.REPLACE_FILE, Key.DOCUMENT_ID to id.value) { span ->
        val result = write()
        result.fold(
            ifLeft = { error ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                val fields = mapOf(Key.DOCUMENT_ID to id.value, Key.ERRORS to error.typeName())
                analytics.track(Event.REPLACE_FAILED, fields)
                logger.error(TAG, Event.REPLACE_FAILED, tc = currentTraceContext().toLog(), fields = fields)
            },
            ifRight = { document ->
                val fields = mapOf(Key.DOCUMENT_ID to id.value, Key.SOURCE to document.source.name)
                analytics.track(Event.FILE_REPLACED, fields)
                logger.info(TAG, Event.FILE_REPLACED, tc = currentTraceContext().toLog(), fields = fields)
            },
        )
        result
    }

    /** Times the delete. A delete that fails silently is a document the owner thinks is gone. */
    suspend fun documentDelete(
        id: DocumentId,
        write: suspend () -> Either<DomainError, Unit>,
    ): Either<DomainError, Unit> = traced(Trace.DELETE_DOCUMENT, Key.DOCUMENT_ID to id.value) { span ->
        val result = write()
        result.fold(
            ifLeft = { error ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                val fields = mapOf(Key.DOCUMENT_ID to id.value, Key.ERRORS to error.typeName())
                analytics.track(Event.DELETE_FAILED, fields)
                logger.error(TAG, Event.DELETE_FAILED, tc = currentTraceContext().toLog(), fields = fields)
            },
            ifRight = {
                analytics.track(Event.DOCUMENT_DELETED)
                logger.info(TAG, Event.DOCUMENT_DELETED, tc = currentTraceContext().toLog(), fields = mapOf(Key.DOCUMENT_ID to id.value))
            },
        )
        result
    }

    /* ------------------------------ Share ------------------------------ */

    fun shareOpened() {
        analytics.track(Event.SHARE_OPENED)
        logger.debug(TAG, Event.SHARE_OPENED, tc = flowTrace.toLog())
    }

    /** A document left the app. [target] is how; the document itself is never emitted. */
    fun documentShared(type: DocumentType, target: String) {
        val fields = mapOf(Key.TYPE to type.name, Key.TARGET to target)
        analytics.track(Event.DOCUMENT_SHARED, fields)
        logger.info(TAG, Event.DOCUMENT_SHARED, tc = flowTrace.toLog(), fields = fields)
    }

    /* ------------------------------ Plumbing ------------------------------ */

    /**
     * Open a span on the calling op's trace, run [block], and close the span whichever way
     * it goes. A cancelled or throwing block still ends its span; an unclosed span reads on
     * a dashboard as an operation that never happened.
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

    /**
     * A failed save, named by the *types* of the errors that caused it, never by the values
     * that failed validation. A limit failure is tracked as its own event: it is a pricing
     * signal, not a bug.
     */
    private fun saveFailed(type: DocumentType, errors: NonEmptyList<DomainError>) {
        val limit = errors.filterIsInstance<DomainError.DocumentLimitReached>().firstOrNull()
        if (limit != null) {
            val fields = mapOf(Key.TYPE to type.name, Key.LIMIT to limit.limit)
            analytics.track(Event.LIMIT_REACHED, fields)
            logger.info(TAG, Event.LIMIT_REACHED, tc = flowTrace.toLog(), fields = fields)
            return
        }
        val fields = mapOf(Key.TYPE to type.name, Key.ERRORS to errors.typeNames())
        analytics.track(Event.SAVE_FAILED, fields)
        logger.error(TAG, Event.SAVE_FAILED, tc = flowTrace.toLog(), fields = fields)
    }

    /** Bridge the coroutine-propagating performance trace onto the logging DTO. */
    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    private fun DomainError.typeName(): String = this::class.simpleName ?: UNKNOWN

    private fun NonEmptyList<DomainError>.typeNames(): String = joinToString(",") { it.typeName() }

    private companion object {
        const val TAG = "DOCUMENTS"
        const val FLOW = "documents"
        const val UNKNOWN = "Unknown"
    }

    /*
     * The feature's observability taxonomy. These names are what a dashboard queries, so
     * they are shipped contracts: reuse one rather than inventing a synonym, and do not
     * rename one without knowing you are breaking its history.
     */

    /** Analytics event names. Declared for the tracker in `documentVaultAnalyticsEvents`. */
    object Event {
        const val VAULT_OPENED = "documents_vault_opened"
        const val READ_FAILED = "documents_read_failed"
        const val DOCUMENT_OPENED = "documents_document_opened"
        const val DOCUMENT_MISSING = "documents_document_missing"
        const val FILE_MISSING = "documents_file_missing"
        const val ADD_OPENED = "documents_add_opened"
        const val TYPE_SELECTED = "documents_type_selected"
        const val CAPTURE_STARTED = "documents_capture_started"
        const val CAPTURE_UNAVAILABLE = "documents_capture_unavailable"
        const val DOCUMENT_ADDED = "documents_document_added"
        const val SAVE_FAILED = "documents_save_failed"
        const val LIMIT_REACHED = "documents_limit_reached"
        const val FILE_REPLACED = "documents_file_replaced"
        const val REPLACE_FAILED = "documents_replace_failed"
        const val DOCUMENT_DELETED = "documents_document_deleted"
        const val DELETE_FAILED = "documents_delete_failed"
        const val SHARE_OPENED = "documents_share_opened"
        const val DOCUMENT_SHARED = "documents_document_shared"
    }

    /** Span names for the feature's async operations. */
    object Trace {
        const val SAVE_DOCUMENT = "documents_save"
        const val REPLACE_FILE = "documents_replace_file"
        const val DELETE_DOCUMENT = "documents_delete"
    }

    /** Property names carried by the events and spans above. */
    object Key {
        const val TYPE = "type"
        const val SOURCE = "source"
        const val METHOD = "method"
        const val REASON = "reason"
        const val ERRORS = "errors"
        const val OUTCOME = "outcome"
        const val DOCUMENT_ID = "document_id"
        const val VERIFIED = "verified"
        const val HAS_EXPIRY = "has_expiry"
        const val PREFILLED = "prefilled"
        const val ON_FILE_COUNT = "on_file_count"
        const val MISSING_COUNT = "missing_count"
        const val ATTENTION_COUNT = "attention_count"
        const val TARGET = "target"
        const val LIMIT = "limit"
    }

    /** Values for [Key.OUTCOME]. */
    object Outcome {
        const val FAILED = "failed"
    }

    /** Values for [Key.SOURCE] when it names a screen rather than a document's provenance. */
    object Screen {
        const val VAULT = "vault"
        const val DETAIL = "detail"
        const val SHARE = "share"
        const val SUCCESS = "success"
    }

    /** Values for [Key.METHOD]. */
    object CaptureMethod {
        const val SCAN = "scan"
        const val UPLOAD = "upload"
        const val DIGILOCKER = "digilocker"
    }
}
