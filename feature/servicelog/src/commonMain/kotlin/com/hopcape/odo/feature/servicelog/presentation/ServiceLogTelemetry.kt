package com.hopcape.odo.feature.servicelog.presentation

import arrow.core.Either
import arrow.core.EitherNel
import arrow.core.NonEmptyList
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.fairness.model.OverchargeReason
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.verification
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.servicelog.presentation.list.model.ServiceLogDirection
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import com.hopcape.performance.api.currentTraceContext
import kotlin.coroutines.CoroutineContext
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * All observability for the service log, behind intent-named methods — so the five
 * ViewModels read as their screens' logic rather than as a wall of
 * `logger`/`analytics`/`tracer` calls.
 *
 * One class owns the three ports, the whole taxonomy ([Event]/[Trace]/[Key], reachable as
 * `ServiceLogTelemetry.Event.*`), and the trace plumbing:
 *
 *  - [flowTrace] is minted per instance. A Koin `factory`, so one instance covers one visit
 *    to the service log; every screen of the feature shares the same [FLOW] id, which is
 *    what stitches list → detail → report into one journey across four destinations.
 *  - [op] hands back the child-trace [CoroutineContext] to launch an async op under. The
 *    `suspend` methods then read [currentTraceContext] themselves, so a span's traceId and
 *    its log lines match that op without the caller threading anything.
 *
 * **Only non-PII context is emitted** — counts, booleans, enum names, entry ids and error
 * *type* names. Never a workshop's name, an amount, an odometer reading or a note: what an
 * owner paid and how far they have driven is their financial and resale-relevant data, and
 * reports are anonymous to the workshop by design. Instrumentation stays at this
 * presentation layer; `:core:domain` remains framework-free.
 *
 * Every method is fire-and-forget by contract: nothing here returns a decision, and the
 * wrapping methods hand back their block's result untouched, so instrumentation can never
 * change what a screen does.
 */
internal class ServiceLogTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    private val tracer: PerformanceTracer,
    private val ids: IdGenerator,
) {

    private val flowTraceId: String = "${FLOW}_${ids.newId()}"
    private val flowTrace = PerfTrace(flowId = FLOW, traceId = flowTraceId)

    /** The child-trace context to `launch(...)` an async op under (`"<name>_<id>"`). */
    fun op(name: String): CoroutineContext = flowTrace.withNewTrace("${name}_${ids.newId()}")

    /* ------------------------------ List ------------------------------ */

    fun listOpened() {
        analytics.track(Event.LIST_OPENED)
        logger.info(TAG, Event.LIST_OPENED, tc = flowTrace.toLog())
    }

    /**
     * A read of the local DB failed, on the screen named by [source].
     *
     * The local DB is the source of truth for an offline-first app, so this is a broken
     * record rather than a slow network — and it is otherwise invisible twice over: the
     * screen just sits on whatever it was showing, and an unhandled failure inside a
     * `collect` would take the ViewModel's scope down with it.
     */
    fun readFailed(source: String, cause: Throwable) {
        val fields = mapOf(Key.SOURCE to source, Key.REASON to (cause::class.simpleName ?: UNKNOWN))
        analytics.track(Event.READ_FAILED, fields)
        logger.error(TAG, Event.READ_FAILED, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * Which of the two list directions owners actually use. Tracked because the ledger and
     * the timeline are two competing answers to the same screen, and this is the number
     * that retires one of them.
     */
    fun directionSelected(direction: ServiceLogDirection) {
        analytics.track(Event.DIRECTION_SELECTED, mapOf(Key.DIRECTION to direction.name))
        logger.debug(TAG, Event.DIRECTION_SELECTED, tc = flowTrace.toLog(), fields = mapOf(Key.DIRECTION to direction.name))
    }

    fun filterSelected(filter: Enum<*>) {
        analytics.track(Event.FILTER_SELECTED, mapOf(Key.FILTER to filter.name))
    }

    /**
     * A scan was launched from the service log. Bills scanned per month is the product's
     * North Star (PRD), so where each scan started matters as much as that it happened.
     */
    fun scanBillClicked(from: String) {
        analytics.track(Event.SCAN_CLICKED, mapOf(Key.SOURCE to from))
        logger.info(TAG, Event.SCAN_CLICKED, tc = flowTrace.toLog(), fields = mapOf(Key.SOURCE to from))
    }

    /* ------------------------------ Detail ------------------------------ */

    /** An entry was opened, described by the two things that decide what it shows. */
    fun entryOpened(verified: Boolean, flagged: Boolean) {
        val fields = mapOf(Key.VERIFIED to verified, Key.FLAGGED to flagged)
        analytics.track(Event.ENTRY_OPENED, fields)
        logger.debug(TAG, Event.ENTRY_OPENED, tc = flowTrace.toLog(), fields = fields)
    }

    fun entryMissing(id: ServiceLogId) {
        logger.warn(TAG, Event.ENTRY_MISSING, tc = flowTrace.toLog(), fields = mapOf(Key.LOG_ID to id.value))
    }

    /* ------------------------------ Async ops ------------------------------ */

    /**
     * Times the add/edit write and records the outcome. [edit] separates a correction from
     * a new entry — a log with many edits is a form that is getting something wrong.
     */
    suspend fun entrySave(
        edit: Boolean,
        categories: Set<ServiceCategory>,
        write: suspend () -> EitherNel<DomainError, ServiceLogEntry>,
    ): EitherNel<DomainError, ServiceLogEntry> = traced(Trace.SAVE_ENTRY, Key.EDIT to edit) { span ->
        val result = write()
        result.fold(
            ifLeft = { errors ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                saveFailed(edit, errors)
            },
            ifRight = { entry ->
                span.setAttribute(Key.LOG_ID, entry.id.value)
                val fields = mapOf(
                    Key.EDIT to edit,
                    Key.VERIFIED to (entry.verification == VerificationStatus.VERIFIED),
                    Key.CATEGORIES to categories.joinToString(",") { it.name },
                )
                analytics.track(Event.ENTRY_SAVED, fields)
                logger.info(TAG, Event.ENTRY_SAVED, tc = currentTraceContext().toLog(), fields = fields)
            },
        )
        result
    }

    /** Times the soft delete. A delete that fails silently is a row the owner thinks is gone. */
    suspend fun entryDelete(
        id: ServiceLogId,
        write: suspend () -> Either<DomainError, Unit>,
    ): Either<DomainError, Unit> = traced(Trace.DELETE_ENTRY, Key.LOG_ID to id.value) { span ->
        val result = write()
        result.fold(
            ifLeft = { error ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                val fields = mapOf(Key.LOG_ID to id.value, Key.ERRORS to error.typeName())
                analytics.track(Event.DELETE_FAILED, fields)
                logger.error(TAG, Event.DELETE_FAILED, tc = currentTraceContext().toLog(), fields = fields)
            },
            ifRight = {
                analytics.track(Event.ENTRY_DELETED)
                logger.info(TAG, Event.ENTRY_DELETED, tc = currentTraceContext().toLog(), fields = mapOf(Key.LOG_ID to id.value))
            },
        )
        result
    }

    /**
     * Times attaching a bill — the copy into app storage plus the write.
     *
     * Worth its own span because it is the slowest thing the detail screen does and the one
     * that changes what the entry is worth: a verified entry is resale proof and is the only
     * kind fairness will judge. A failure here is a photo the owner picked and lost.
     */
    suspend fun billAttach(
        id: ServiceLogId,
        write: suspend () -> Either<DomainError, ServiceLogEntry>,
    ): Either<DomainError, ServiceLogEntry> = traced(Trace.ATTACH_BILL, Key.LOG_ID to id.value) { span ->
        val result = write()
        result.fold(
            ifLeft = { error ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                val fields = mapOf(Key.LOG_ID to id.value, Key.ERRORS to error.typeName())
                analytics.track(Event.ATTACH_FAILED, fields)
                logger.error(TAG, Event.ATTACH_FAILED, tc = currentTraceContext().toLog(), fields = fields)
            },
            ifRight = {
                analytics.track(Event.BILL_ATTACHED)
                logger.info(TAG, Event.BILL_ATTACHED, tc = currentTraceContext().toLog(), fields = mapOf(Key.LOG_ID to id.value))
            },
        )
        result
    }

    /**
     * A verdict was stored on an entry. [outcome] is the result's type name, or `null` when
     * the check found nothing to compare against — the gap the fairness pool has to close,
     * and invisible unless it is counted.
     */
    fun fairnessRecorded(outcome: String?) {
        val fields = mapOf(Key.OUTCOME to (outcome ?: NONE))
        analytics.track(Event.FAIRNESS_RECORDED, fields)
        logger.info(TAG, Event.FAIRNESS_RECORDED, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * The verdict could not be written. The bill is attached either way, so this is a
     * missing badge rather than lost work — but it is silent, which is why it is logged.
     */
    fun fairnessRecordFailed(id: ServiceLogId, error: DomainError) {
        val fields = mapOf(Key.LOG_ID to id.value, Key.ERRORS to error.typeName())
        analytics.track(Event.FAIRNESS_FAILED, fields)
        logger.error(TAG, Event.FAIRNESS_FAILED, tc = flowTrace.toLog(), fields = fields)
    }

    /** "Check fairness" opened the shared report from an entry. */
    fun fairnessOpened() {
        analytics.track(Event.FAIRNESS_OPENED)
        logger.debug(TAG, Event.FAIRNESS_OPENED, tc = flowTrace.toLog())
    }

    /**
     * Times the overcharge report. The [reason] travels because it is the whole point of
     * the report — which kind of overcharging owners hit is what the fairness pool learns.
     * The note never does: it is free text the owner wrote about a named workshop.
     */
    suspend fun reportSubmit(
        reason: OverchargeReason,
        write: suspend () -> Either<DomainError, Unit>,
    ): Either<DomainError, Unit> = traced(Trace.SUBMIT_REPORT, Key.REASON to reason.name) { span ->
        val result = write()
        result.fold(
            ifLeft = { error ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                val fields = mapOf(Key.REASON to reason.name, Key.ERRORS to error.typeName())
                analytics.track(Event.REPORT_FAILED, fields)
                logger.error(TAG, Event.REPORT_FAILED, tc = currentTraceContext().toLog(), fields = fields)
            },
            ifRight = {
                analytics.track(Event.REPORT_SUBMITTED, mapOf(Key.REASON to reason.name))
                logger.info(TAG, Event.REPORT_SUBMITTED, tc = currentTraceContext().toLog(), fields = mapOf(Key.REASON to reason.name))
            },
        )
        result
    }

    /* ------------------------------ Share ------------------------------ */

    /**
     * The owner opened the attached bill itself.
     *
     * The counterpart to [billAttach]: attaching is what verifies an entry, and this is
     * whether anyone ever looks at the proof again. A bill that is filed and never opened is
     * a bill the resale passport is the only reader of.
     */
    fun billViewed() {
        analytics.track(Event.BILL_VIEWED)
        logger.debug(TAG, Event.BILL_VIEWED, tc = flowTrace.toLog())
    }

    fun shareOpened() {
        analytics.track(Event.SHARE_OPENED)
        logger.debug(TAG, Event.SHARE_OPENED, tc = flowTrace.toLog())
    }

    /**
     * A verified record left the app. [target] is how (link, WhatsApp, PDF…) — the shared
     * record itself is never emitted, only that sharing happened.
     */
    fun recordShared(target: String) {
        analytics.track(Event.RECORD_SHARED, mapOf(Key.TARGET to target))
        logger.info(TAG, Event.RECORD_SHARED, tc = flowTrace.toLog(), fields = mapOf(Key.TARGET to target))
    }

    /**
     * A free owner asked for the whole record and met the Pro gate.
     *
     * Counted whether or not they go on to subscribe: how often the export is wanted is the
     * thing that says whether it was worth selling, and only the refusals show that.
     */
    fun recordExportLocked() {
        analytics.track(Event.RECORD_EXPORT_LOCKED)
        logger.info(TAG, Event.RECORD_EXPORT_LOCKED, tc = flowTrace.toLog())
    }

    /* ------------------------------ Plumbing ------------------------------ */

    /**
     * Open a span on the calling op's trace, run [block], and close the span whichever way
     * it goes. A cancelled or throwing block still ends its span — an unclosed span is a
     * duration that never arrives, which reads on a dashboard as an operation that never
     * happened.
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
     * A failed write, named by the *types* of the errors that caused it — never by the
     * values that failed validation, which are the owner's own answers.
     */
    private suspend fun saveFailed(edit: Boolean, errors: NonEmptyList<DomainError>) {
        val fields = mapOf(Key.EDIT to edit, Key.ERRORS to errors.typeNames())
        analytics.track(Event.SAVE_FAILED, fields)
        logger.error(TAG, Event.SAVE_FAILED, tc = currentTraceContext().toLog(), fields = fields)
    }

    /** Bridge the coroutine-propagating performance trace onto the logging DTO. */
    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    private companion object {
        const val TAG = "SERVICELOG"
        const val FLOW = "servicelog"
        const val UNKNOWN = "Unknown"

        /** No verdict at all — the check ran and found nothing to compare against. */
        const val NONE = "None"
    }

    /*
     * The feature's observability taxonomy below — the single source of truth for every
     * event, span and field name. These names are what a dashboard queries, so treat them
     * as shipped contracts: reuse one rather than inventing a synonym, and don't rename one
     * without knowing you are breaking its history.
     */

    /** Analytics event names — the service-log journey, list → entry → record shared. */
    object Event {
        const val LIST_OPENED = "servicelog_list_opened"
        const val READ_FAILED = "servicelog_read_failed"
        const val DIRECTION_SELECTED = "servicelog_direction_selected"
        const val FILTER_SELECTED = "servicelog_filter_selected"
        const val SCAN_CLICKED = "servicelog_scan_clicked"

        const val ENTRY_OPENED = "servicelog_entry_opened"
        const val ENTRY_MISSING = "servicelog_entry_missing"
        const val ENTRY_SAVED = "servicelog_entry_saved"
        const val SAVE_FAILED = "servicelog_save_failed"
        const val ENTRY_DELETED = "servicelog_entry_deleted"
        const val DELETE_FAILED = "servicelog_delete_failed"

        const val BILL_ATTACHED = "servicelog_bill_attached"
        const val BILL_VIEWED = "servicelog_bill_viewed"
        const val ATTACH_FAILED = "servicelog_attach_failed"
        const val FAIRNESS_RECORDED = "servicelog_fairness_recorded"
        const val FAIRNESS_FAILED = "servicelog_fairness_failed"
        const val FAIRNESS_OPENED = "servicelog_fairness_opened"

        const val REPORT_SUBMITTED = "servicelog_report_submitted"
        const val REPORT_FAILED = "servicelog_report_failed"

        const val SHARE_OPENED = "servicelog_share_opened"
        const val RECORD_EXPORT_LOCKED = "record_export_locked"
        const val RECORD_SHARED = "servicelog_record_shared"
    }

    /** Span / child-trace names — only genuine durations (DB reads and writes). */
    object Trace {
        const val FEED_LOAD = "servicelog_feed_load"
        const val ENTRY_LOAD = "servicelog_entry_load"
        const val SAVE_ENTRY = "servicelog_save_entry"
        const val DELETE_ENTRY = "servicelog_delete_entry"
        const val ATTACH_BILL = "servicelog_attach_bill"
        const val CHECK_FAIRNESS = "servicelog_check_fairness"
        const val SUBMIT_REPORT = "servicelog_submit_report"
        const val SHARE_LOAD = "servicelog_share_load"
        const val RECORD_EXPORT = "servicelog_record_export"
    }

    /** Structured field / property keys, shared across logs, events and spans. */
    object Key {
        const val LOG_ID = "log_id"
        const val EDIT = "edit"
        const val VERIFIED = "verified"
        const val FLAGGED = "flagged"
        const val CATEGORIES = "categories"
        const val DIRECTION = "direction"
        const val FILTER = "filter"
        const val SOURCE = "source"
        const val TARGET = "target"
        const val REASON = "reason"
        const val OUTCOME = "outcome"
        const val ERRORS = "errors"
    }

    /** The values [Key.OUTCOME] takes, beyond a `DomainError`'s own type name. */
    object Outcome {
        const val FAILED = "failed"
    }

    /**
     * Which screen an event came from — [Key.SOURCE] values, shared by the scan entry
     * points and by [readFailed] so one vocabulary names the feature's surfaces.
     */
    object Source {
        const val LIST = "list"
        const val FORM = "form"
        const val DETAIL = "detail"
        const val REPORT = "report"
        const val SHARE = "share"
    }
}

private fun DomainError.typeName(): String = this::class.simpleName ?: "Unknown"

/**
 * A comma-joined list of the accumulated errors' *type* names — safe to emit (no user input,
 * no PII) and enough for a dashboard to see which rule failed.
 */
private fun NonEmptyList<DomainError>.typeNames(): String = joinToString(",") { it.typeName() }
