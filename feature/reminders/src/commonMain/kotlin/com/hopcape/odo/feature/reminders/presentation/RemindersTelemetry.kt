package com.hopcape.odo.feature.reminders.presentation

import arrow.core.Either
import arrow.core.EitherNel
import arrow.core.NonEmptyList
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.reminder.model.ReminderKind
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import com.hopcape.performance.api.currentTraceContext
import kotlin.coroutines.CoroutineContext
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * All observability for the reminders feature, behind intent-named methods, so the
 * ViewModels read as their screens' logic instead of a wall of logger, analytics and
 * tracer calls. One class owns the three ports and the taxonomy ([Event], [Key]).
 *
 * [flowTrace] is minted per instance. Registered as a Koin `factory`, so one instance
 * covers one visit to reminders and every screen of that visit shares one flow id.
 *
 * **No PII.** Kinds, cadence names, counts, booleans and error *type* names only — never
 * a reminder's title. A title is text the owner typed about their own car.
 *
 * Every method is fire-and-forget: nothing here returns a decision, and the wrapping
 * methods hand back their block's result untouched, so instrumentation cannot change
 * what a screen does.
 */
internal class RemindersTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    private val tracer: PerformanceTracer,
    private val ids: IdGenerator,
) {

    private val flowTraceId: String = "${FLOW}_${ids.newId()}"
    private val flowTrace = PerfTrace(flowId = FLOW, traceId = flowTraceId)

    /** The child-trace context to `launch(...)` an async op under. */
    fun op(name: String): CoroutineContext = flowTrace.withNewTrace("${name}_${ids.newId()}")

    /* ------------------------------ List ------------------------------ */

    /** The list was opened, with what it found. These counts are the retention story. */
    fun opened(thisWeek: Int, upcoming: Int, suggestions: Int) {
        val fields = mapOf(
            Key.THIS_WEEK_COUNT to thisWeek,
            Key.UPCOMING_COUNT to upcoming,
            Key.SUGGESTION_COUNT to suggestions,
        )
        analytics.track(Event.OPENED, fields)
        logger.info(TAG, Event.OPENED, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * A read of the local DB failed on the screen named by [source]. The local DB is the
     * source of truth, so this is a broken record, and otherwise invisible.
     */
    fun readFailed(source: String, cause: Throwable) {
        val fields = mapOf(Key.SOURCE to source, Key.REASON to (cause::class.simpleName ?: UNKNOWN))
        analytics.track(Event.READ_FAILED, fields)
        logger.error(TAG, Event.READ_FAILED, tc = flowTrace.toLog(), fields = fields)
    }

    /* ------------------------------ Actions ------------------------------ */

    /** Times a dismissal ("not this one") and records the outcome. */
    suspend fun dismiss(
        kind: ReminderKind,
        write: suspend () -> Either<DomainError, Unit>,
    ): Either<DomainError, Unit> = traced(Trace.DISMISS, Key.KIND to kind.name) { span ->
        val result = write()
        result.fold(
            ifLeft = { error ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                failed(Event.DISMISS_FAILED, Key.KIND to kind.name, Key.ERRORS to error.typeName())
            },
            ifRight = {
                val fields = mapOf(Key.KIND to kind.name)
                analytics.track(Event.DISMISSED, fields)
                logger.info(TAG, Event.DISMISSED, tc = currentTraceContext().toLog(), fields = fields)
            },
        )
        result
    }

    /** Times a turn-off — a pause for a custom reminder, a topic flip for a derived one. */
    suspend fun <T> turnOff(
        kind: ReminderKind,
        custom: Boolean,
        write: suspend () -> Either<DomainError, T>,
    ): Either<DomainError, T> = traced(Trace.TURN_OFF, Key.KIND to kind.name) { span ->
        val result = write()
        result.fold(
            ifLeft = { error ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                failed(Event.TURN_OFF_FAILED, Key.KIND to kind.name, Key.ERRORS to error.typeName())
            },
            ifRight = {
                val fields = mapOf(Key.KIND to kind.name, Key.CUSTOM to custom)
                analytics.track(Event.TURNED_OFF, fields)
                logger.info(TAG, Event.TURNED_OFF, tc = currentTraceContext().toLog(), fields = fields)
            },
        )
        result
    }

    /* ------------------------------ Create / edit ------------------------------ */

    /**
     * Times a custom reminder save and records the outcome. [cadence] is the cadence's
     * type name; [viaSuggestion] separates the one-tap opt-in from the full form.
     */
    suspend fun <T> reminderSave(
        cadence: String,
        preset: String?,
        viaSuggestion: Boolean,
        edit: Boolean,
        write: suspend () -> EitherNel<DomainError, T>,
    ): EitherNel<DomainError, T> = traced(Trace.SAVE, Key.CADENCE to cadence) { span ->
        val result = write()
        result.fold(
            ifLeft = { errors ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                failed(Event.SAVE_FAILED, Key.CADENCE to cadence, Key.ERRORS to errors.typeNames())
            },
            ifRight = {
                val event = if (edit) Event.CUSTOM_EDITED else Event.CUSTOM_CREATED
                val fields = mapOf(
                    Key.CADENCE to cadence,
                    Key.PRESET to (preset ?: NONE),
                    Key.VIA_SUGGESTION to viaSuggestion,
                )
                analytics.track(event, fields)
                logger.info(TAG, event, tc = currentTraceContext().toLog(), fields = fields)
            },
        )
        result
    }

    /** A custom reminder was deleted from the edit form. */
    fun customDeleted() {
        analytics.track(Event.CUSTOM_DELETED)
        logger.info(TAG, Event.CUSTOM_DELETED, tc = flowTrace.toLog())
    }

    /* ------------------------------ Settings ------------------------------ */

    /** Times a settings toggle and records the outcome. [field] is the preference's name. */
    suspend fun <T> settingsToggle(
        field: String,
        enabled: Boolean,
        write: suspend () -> Either<DomainError, T>,
    ): Either<DomainError, T> = traced(Trace.TOGGLE, Key.FIELD to field) { span ->
        val result = write()
        result.fold(
            ifLeft = { error ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                failed(Event.SETTINGS_FAILED, Key.FIELD to field, Key.ERRORS to error.typeName())
            },
            ifRight = {
                val fields = mapOf(Key.FIELD to field, Key.ENABLED to enabled)
                analytics.track(Event.SETTINGS_CHANGED, fields)
                logger.info(TAG, Event.SETTINGS_CHANGED, tc = currentTraceContext().toLog(), fields = fields)
            },
        )
        result
    }

    /* ------------------------------ Plumbing ------------------------------ */

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

    private suspend fun failed(event: String, vararg fields: Pair<String, Any?>) {
        val map = fields.toMap()
        analytics.track(event, map)
        logger.error(TAG, event, tc = currentTraceContext().toLog(), fields = map)
    }

    /** Bridge the coroutine-propagating performance trace onto the logging DTO. */
    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    private fun DomainError.typeName(): String = this::class.simpleName ?: UNKNOWN

    private fun NonEmptyList<DomainError>.typeNames(): String = joinToString(",") { it.typeName() }

    private companion object {
        const val TAG = "REMINDERS"
        const val FLOW = "reminders"
        const val UNKNOWN = "Unknown"
        const val NONE = "none"
    }

    /*
     * The feature's observability taxonomy. These names are what a dashboard queries, so
     * they are shipped contracts: reuse one rather than inventing a synonym.
     */

    /** Analytics event names. Declared for the tracker in `remindersAnalyticsEvents`. */
    object Event {
        const val OPENED = "reminders_opened"
        const val READ_FAILED = "reminders_read_failed"
        const val DISMISSED = "reminders_dismissed"
        const val DISMISS_FAILED = "reminders_dismiss_failed"
        const val TURNED_OFF = "reminders_turned_off"
        const val TURN_OFF_FAILED = "reminders_turn_off_failed"
        const val CUSTOM_CREATED = "reminders_custom_created"
        const val CUSTOM_EDITED = "reminders_custom_edited"
        const val CUSTOM_DELETED = "reminders_custom_deleted"
        const val SAVE_FAILED = "reminders_save_failed"
        const val SETTINGS_CHANGED = "reminders_settings_changed"
        const val SETTINGS_FAILED = "reminders_settings_failed"
    }

    /** Field keys. */
    object Key {
        const val THIS_WEEK_COUNT = "this_week_count"
        const val UPCOMING_COUNT = "upcoming_count"
        const val SUGGESTION_COUNT = "suggestion_count"
        const val SOURCE = "source"
        const val REASON = "reason"
        const val KIND = "kind"
        const val CUSTOM = "custom"
        const val CADENCE = "cadence"
        const val PRESET = "preset"
        const val VIA_SUGGESTION = "via_suggestion"
        const val ERRORS = "errors"
        const val FIELD = "field"
        const val ENABLED = "enabled"
        const val OUTCOME = "outcome"
    }

    /** Span names. */
    object Trace {
        const val DISMISS = "reminders.dismiss"
        const val TURN_OFF = "reminders.turnOff"
        const val SAVE = "reminders.save"
        const val TOGGLE = "reminders.settingsToggle"
    }

    object Outcome {
        const val FAILED = "failed"
    }

    /** Screen names for [readFailed]. */
    object Screen {
        const val LIST = "list"
        const val NEW = "new"
        const val SETTINGS = "settings"
    }
}
