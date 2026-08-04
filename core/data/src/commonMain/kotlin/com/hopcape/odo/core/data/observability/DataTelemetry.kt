package com.hopcape.odo.core.data.observability

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.Logger
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.currentTraceContext
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * Observability for the data layer, behind one intent-named surface — the repository
 * equivalent of a feature's `<Feature>Telemetry` facade. Repositories read as their own
 * logic instead of a wall of `tracer`/`logger`/`crash` calls, and every data-layer event
 * name lives in one file.
 *
 * **The trace is never a parameter.** [span] reads the [PerfTrace] installed on the calling
 * coroutine via [currentTraceContext], so a repository picks up whatever trace its caller
 * started without it being threaded through every signature. Today that context is usually
 * empty; the moment a ViewModel does `launch(telemetry.op("addLog")) { … }`, these spans and
 * logs join that trace with no change here. The two observability modules each declare their
 * own `TraceContext` type, so [toLog] is the one place that bridges them.
 *
 * **Never log PII.** Callers pass ids and operation names only — never a workshop name,
 * notes, a registration number, or a stored file path. A failure is reported by its error
 * *type*, never by the value that caused it.
 *
 * Fire-and-forget by contract: nothing here returns a decision and [span] hands back its
 * block's result untouched, so instrumentation can never change what a repository does.
 */
internal class DataTelemetry(
    private val logger: Logger,
    private val tracer: PerformanceTracer,
    private val crash: CrashRecorder,
) {

    /**
     * Run [block] inside a span named `<entity>.<operation>`, on the caller's trace.
     *
     * The span is closed in a `finally`, so a throwing block still ends its span rather
     * than leaking an open one into the export queue.
     */
    suspend fun <T> span(entity: String, operation: String, id: String? = null, block: suspend () -> T): T {
        val trace = currentTraceContext()
        val span = tracer.startSpan(name = "$entity.$operation", traceId = trace.traceId ?: UNTRACED)
        return try {
            block()
        } finally {
            tracer.endSpan(span)
            id?.let { logger.debug(TAG, "$entity.$operation", tc = trace.toLog(), fields = mapOf(Key.ID to it)) }
        }
    }

    /**
     * A write or read that failed with a domain error — the case that is otherwise
     * invisible, because the caller only sees an `Either.Left`.
     */
    suspend fun failed(entity: String, operation: String, error: Any, id: String? = null) {
        logger.error(
            TAG,
            "$entity.$operation.failed",
            tc = currentTraceContext().toLog(),
            fields = buildMap {
                // The error's *type*, never a message that could carry user input.
                put(Key.ERROR, error::class.simpleName)
                id?.let { put(Key.ID, it) }
            },
        )
    }

    /**
     * A lookup that found nothing — reference data Odo does not carry yet, not a failure.
     *
     * Reported because the alternative is silence: a fuel price missing for a city looks
     * exactly like a car with no fuel type, and only the first one is a coverage gap
     * someone can close. [key] is reference data (a city, a fuel type), never anything
     * that identifies an owner.
     */
    suspend fun missing(entity: String, operation: String, key: String) {
        logger.warn(
            TAG,
            "$entity.$operation.missing",
            tc = currentTraceContext().toLog(),
            fields = mapOf(Key.KEY to key),
        )
    }

    /**
     * An exception this layer caught and turned into a `DomainError.PersistenceFailure`.
     * Recorded as a non-fatal because a swallowed exception is exactly the kind of broken
     * that never reaches a crash dashboard on its own.
     */
    suspend fun crashed(entity: String, operation: String, throwable: Throwable, id: String? = null) {
        crash.recordNonFatal(throwable, buildMap {
            put(Key.ENTITY, entity)
            put(Key.OPERATION, operation)
            id?.let { put(Key.ID, it) }
        })
        failed(entity, operation, throwable, id)
    }

    /** Bridges the performance module's coroutine-carried trace to the logging module's. */
    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    /** Field keys — kept here so a dashboard query never breaks on a renamed literal. */
    internal object Key {
        const val ID = "id"
        const val ENTITY = "entity"
        const val OPERATION = "operation"
        const val ERROR = "error"
        const val KEY = "key"
    }

    internal companion object {
        const val TAG = "data"

        /** Span traceId when no caller installed a trace — grouped rather than dropped. */
        const val UNTRACED = "untraced"

        /* Entity names used as the first half of every span/event name. */
        const val CAR = "car"
        const val SERVICE_LOG = "servicelog"
        const val FAIRNESS = "fairness"
        const val OVERCHARGE = "overcharge"
        const val PROFILE = "profile"
        const val DOCUMENT = "document"
        const val FUEL_PRICE = "fuelprice"

        /** Fuel fills — the measured half of the running cost, kept apart from the price feed. */
        const val FUEL_FILL = "fuelfill"
        const val HEALTH_SCORE = "healthscore"
        const val SYNC = "sync"
    }
}
