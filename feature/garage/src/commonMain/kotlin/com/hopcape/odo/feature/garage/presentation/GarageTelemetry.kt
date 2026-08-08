package com.hopcape.odo.feature.garage.presentation

import arrow.core.Either
import arrow.core.EitherNel
import arrow.core.NonEmptyList
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import com.hopcape.performance.api.currentTraceContext
import kotlin.coroutines.CoroutineContext
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * All observability for the garage, behind intent-named methods, so the ViewModels read as
 * their screens' logic instead of a wall of logger, analytics and tracer calls.
 *
 * One class owns the three ports, the taxonomy ([Event], [Trace], [Key]) and the trace
 * plumbing:
 *
 *  - [flowTrace] is minted per instance. Registered as a Koin `factory`, so one instance
 *    covers one visit to the garage and every screen of that visit shares one flow id.
 *  - [op] returns the child-trace context to launch an async op under. The `suspend`
 *    methods read [currentTraceContext] themselves, so a span and its log lines match
 *    without the caller passing anything.
 *
 * **No PII.** Counts, booleans, ids and error *type* names only — never a registration
 * number, a nickname, a workshop name or an odometer reading. A plate identifies a person's
 * vehicle, and a nickname is text they wrote about their own car.
 *
 * Every method is fire-and-forget: nothing here returns a decision, and the wrapping methods
 * hand back their block's result untouched, so instrumentation cannot change what a screen
 * does.
 */
internal class GarageTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    private val tracer: PerformanceTracer,
    private val ids: IdGenerator,
) {

    private val flowTraceId: String = "${FLOW}_${ids.newId()}"
    private val flowTrace = PerfTrace(flowId = FLOW, traceId = flowTraceId)

    /** The child-trace context to `launch(...)` an async op under. */
    fun op(name: String): CoroutineContext = flowTrace.withNewTrace("${name}_${ids.newId()}")

    /* ------------------------------ Home base ------------------------------ */

    /**
     * The garage was opened, with what it found: how much history is on file, how much of
     * it is bill-backed, and how many papers are missing or lapsing.
     *
     * These are the numbers the feature exists to move — a garage that stays empty is the
     * drop-off worth knowing about.
     */
    fun garageOpened(services: Int, verified: Int, documentsMissing: Int, documentsExpiring: Int) {
        val fields = mapOf(
            Key.SERVICE_COUNT to services,
            Key.VERIFIED_COUNT to verified,
            Key.MISSING_COUNT to documentsMissing,
            Key.ATTENTION_COUNT to documentsExpiring,
        )
        analytics.track(Event.GARAGE_OPENED, fields)
        logger.info(TAG, Event.GARAGE_OPENED, tc = flowTrace.toLog(), fields = fields)
    }

    /** The garage was opened before a car was set up — the empty state, not a failure. */
    fun garageEmpty() {
        analytics.track(Event.GARAGE_EMPTY)
        logger.debug(TAG, Event.GARAGE_EMPTY, tc = flowTrace.toLog())
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

    /** Which cut of the history owners actually look at. */
    fun historyFiltered(facet: String) {
        analytics.track(Event.HISTORY_FILTERED, mapOf(Key.FACET to facet))
    }

    /**
     * A screen that needs a car was reached without one. Not an error the owner caused —
     * it means a route opened before setup finished, and it is worth counting.
     */
    fun noActiveCar(source: String) {
        val fields = mapOf(Key.SOURCE to source)
        analytics.track(Event.NO_ACTIVE_CAR, fields)
        logger.warn(TAG, Event.NO_ACTIVE_CAR, tc = flowTrace.toLog(), fields = fields)
    }

    /* ------------------------------ Odometer ------------------------------ */

    fun odometerSheetOpened() {
        analytics.track(Event.ODOMETER_OPENED)
        logger.debug(TAG, Event.ODOMETER_OPENED, tc = flowTrace.toLog())
    }

    /**
     * Times the odometer write and records the outcome.
     *
     * A refused reading is tracked as its own event rather than a generic failure: an
     * owner typing a number below their last one is the single most likely way this screen
     * fails, and it is a copy problem, not a bug.
     */
    suspend fun odometerSave(
        write: suspend () -> Either<DomainError, Car>,
    ): Either<DomainError, Car> = traced(Trace.SAVE_ODOMETER) { span ->
        val result = write()
        result.fold(
            ifLeft = { error ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                val event = when (error) {
                    is DomainError.OdometerRegression,
                    is DomainError.OdometerAheadOfLaterEntry,
                    -> Event.ODOMETER_REFUSED

                    else -> Event.ODOMETER_FAILED
                }
                val fields = mapOf(Key.ERRORS to error.typeName())
                analytics.track(event, fields)
                logger.error(TAG, event, tc = currentTraceContext().toLog(), fields = fields)
            },
            ifRight = {
                analytics.track(Event.ODOMETER_UPDATED)
                logger.info(TAG, Event.ODOMETER_UPDATED, tc = currentTraceContext().toLog())
            },
        )
        result
    }

    /* ------------------------------ Car ------------------------------ */

    /** The add-car or edit-car form was opened. */
    fun carFormOpened(source: String) {
        val fields = mapOf(Key.SOURCE to source)
        analytics.track(Event.CAR_FORM_OPENED, fields)
        logger.debug(TAG, Event.CAR_FORM_OPENED, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * A plate lookup came back. [found] is what decides whether the reg-first path is worth
     * keeping: there is no registry wired, so every lookup fails today, and this is the
     * number that would show it starting to work.
     */
    fun plateLookedUp(found: Boolean, error: DomainError?) {
        val fields = mapOf(Key.FOUND to found, Key.ERRORS to (error?.typeName() ?: NONE))
        analytics.track(Event.PLATE_LOOKED_UP, fields)
        logger.info(TAG, Event.PLATE_LOOKED_UP, tc = flowTrace.toLog(), fields = fields)
    }

    /** Times an add or an edit and records the outcome. [source] says which one it was. */
    suspend fun carSave(
        source: String,
        write: suspend () -> EitherNel<DomainError, Car>,
    ): EitherNel<DomainError, Car> = traced(Trace.SAVE_CAR, Key.SOURCE to source) { span ->
        val result = write()
        result.fold(
            ifLeft = { errors ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                // The error *types*, never the values that failed validation.
                val fields = mapOf(Key.SOURCE to source, Key.ERRORS to errors.typeNames())
                analytics.track(Event.CAR_SAVE_FAILED, fields)
                logger.error(TAG, Event.CAR_SAVE_FAILED, tc = currentTraceContext().toLog(), fields = fields)
            },
            ifRight = { car ->
                span.setAttribute(Key.CAR_ID, car.id.value)
                val fields = mapOf(Key.SOURCE to source, Key.HAS_PLATE to (car.registrationNumber != null))
                analytics.track(Event.CAR_SAVED, fields)
                logger.info(TAG, Event.CAR_SAVED, tc = currentTraceContext().toLog(), fields = fields)
            },
        )
        result
    }

    /**
     * Times the removal. [services] and [documents] are what went with the car, which is
     * the number that says how much a mistaken tap costs.
     */
    suspend fun carRemove(
        services: Int,
        documents: Int,
        write: suspend () -> Either<DomainError, Unit>,
    ): Either<DomainError, Unit> = traced(Trace.REMOVE_CAR) { span ->
        val result = write()
        result.fold(
            ifLeft = { error ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                val fields = mapOf(Key.ERRORS to error.typeName())
                analytics.track(Event.CAR_REMOVE_FAILED, fields)
                logger.error(TAG, Event.CAR_REMOVE_FAILED, tc = currentTraceContext().toLog(), fields = fields)
            },
            ifRight = {
                val fields = mapOf(Key.SERVICE_COUNT to services, Key.DOCUMENT_COUNT to documents)
                analytics.track(Event.CAR_REMOVED, fields)
                logger.info(TAG, Event.CAR_REMOVED, tc = currentTraceContext().toLog(), fields = fields)
            },
        )
        result
    }

    /**
     * Export was asked for, which today means the paywall. Counted because it is demand:
     * how often owners reach for their record decides what the Resale Passport is worth.
     */
    fun exportRequested(target: String) {
        val fields = mapOf(Key.TARGET to target)
        analytics.track(Event.EXPORT_REQUESTED, fields)
        logger.info(TAG, Event.EXPORT_REQUESTED, tc = flowTrace.toLog(), fields = fields)
    }

    /* ------------------------------ Auto odometer (F9) ------------------------------ */

    /**
     * The auto-odometer pitch card appeared on the home base — the funnel's first step,
     * counted once per visit.
     */
    fun autoOdometerCardShown() {
        analytics.track(Event.AUTO_ODO_CARD_SHOWN)
        logger.debug(TAG, Event.AUTO_ODO_CARD_SHOWN, tc = flowTrace.toLog())
    }

    /** The card's "See how it works" CTA was tapped, headed to the education screen. */
    fun autoOdometerCardTapped() {
        analytics.track(Event.AUTO_ODO_CARD_TAPPED)
        logger.debug(TAG, Event.AUTO_ODO_CARD_TAPPED, tc = flowTrace.toLog())
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

    /** Bridge the coroutine-propagating performance trace onto the logging DTO. */
    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    private fun DomainError.typeName(): String = this::class.simpleName ?: UNKNOWN

    private fun NonEmptyList<DomainError>.typeNames(): String = joinToString(",") { it.typeName() }

    private companion object {
        const val TAG = "GARAGE"
        const val FLOW = "garage"
        const val UNKNOWN = "Unknown"
        const val NONE = "none"
    }

    /*
     * The feature's observability taxonomy. These names are what a dashboard queries, so
     * they are shipped contracts: reuse one rather than inventing a synonym, and do not
     * rename one without knowing you are breaking its history.
     */

    /** Analytics event names. Declared for the tracker in `garageAnalyticsEvents`. */
    object Event {
        const val GARAGE_OPENED = "garage_opened"
        const val GARAGE_EMPTY = "garage_empty"
        const val READ_FAILED = "garage_read_failed"
        const val HISTORY_FILTERED = "garage_history_filtered"
        const val NO_ACTIVE_CAR = "garage_no_active_car"
        const val ODOMETER_OPENED = "garage_odometer_opened"
        const val ODOMETER_UPDATED = "garage_odometer_updated"
        const val ODOMETER_REFUSED = "garage_odometer_refused"
        const val ODOMETER_FAILED = "garage_odometer_failed"
        const val CAR_FORM_OPENED = "garage_car_form_opened"
        const val PLATE_LOOKED_UP = "garage_plate_looked_up"
        const val CAR_SAVED = "garage_car_saved"
        const val CAR_SAVE_FAILED = "garage_car_save_failed"
        const val CAR_REMOVED = "garage_car_removed"
        const val CAR_REMOVE_FAILED = "garage_car_remove_failed"
        const val EXPORT_REQUESTED = "garage_export_requested"
        const val AUTO_ODO_CARD_SHOWN = "garage_auto_odo_card_shown"
        const val AUTO_ODO_CARD_TAPPED = "garage_auto_odo_card_tapped"
    }

    /** Span names for the feature's async operations. */
    object Trace {
        const val SAVE_ODOMETER = "garage_save_odometer"
        const val SAVE_CAR = "garage_save_car"
        const val REMOVE_CAR = "garage_remove_car"
    }

    /** Property names carried by the events and spans above. */
    object Key {
        const val SOURCE = "source"
        const val REASON = "reason"
        const val ERRORS = "errors"
        const val OUTCOME = "outcome"
        const val FACET = "facet"
        const val FOUND = "found"
        const val CAR_ID = "car_id"
        const val HAS_PLATE = "has_plate"
        const val SERVICE_COUNT = "service_count"
        const val VERIFIED_COUNT = "verified_count"
        const val DOCUMENT_COUNT = "document_count"
        const val MISSING_COUNT = "missing_count"
        const val ATTENTION_COUNT = "attention_count"
        const val TARGET = "target"
    }

    /** Values for [Key.OUTCOME]. */
    object Outcome {
        const val FAILED = "failed"
    }

    /** Values for [Key.SOURCE]. */
    object Screen {
        const val HOME = "home"
        const val ODOMETER = "odometer"
        const val ADD_CAR = "add_car"
        const val EDIT_CAR = "edit_car"
        const val CAR_ACTIONS = "car_actions"
        const val REMOVE_CAR = "remove_car"
        const val EXPORT = "export"
    }

    /** Values for [Key.TARGET] on an export request. */
    object ExportTarget {
        const val PDF = "pdf"
        const val SHARE = "share"
    }
}
