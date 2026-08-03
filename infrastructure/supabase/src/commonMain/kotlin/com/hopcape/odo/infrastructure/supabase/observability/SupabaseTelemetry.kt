package com.hopcape.odo.infrastructure.supabase.observability

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.Logger
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.currentTraceContext
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * Observability for every call that leaves the device, behind one intent-named surface — the
 * network equivalent of `:core:data`'s `DataTelemetry`.
 *
 * Sync failing quietly is the worst failure mode an offline-first app has: the owner keeps
 * using a working app while nothing reaches the server. So every request is spanned, every
 * non-2xx and timeout is logged, and every retry is counted.
 *
 * **The trace is never a parameter.** [span] reads the [PerfTrace] installed on the calling
 * coroutine, so a request joins whatever trace the repository or sync pass started.
 *
 * **Never log PII.** Table names, bucket names, operation names, HTTP status codes and row
 * *counts* only — never a response body, a workshop name, a storage path, or a registration
 * number. A Supabase error body echoes the row that failed, so it is deliberately never
 * logged; the status code is what a dashboard can act on.
 *
 * Fire-and-forget by contract: nothing here returns a decision and [span] hands back its
 * block's result untouched, so instrumentation can never change what a request does.
 */
internal class SupabaseTelemetry(
    private val logger: Logger,
    private val tracer: PerformanceTracer,
    private val crash: CrashRecorder,
) {

    /**
     * Run [block] inside a span named `supabase.<operation>`, on the caller's trace.
     *
     * The span is closed in a `finally`, so a throwing request still ends its span rather
     * than leaking an open one into the export queue.
     */
    suspend fun <T> span(operation: String, resource: String, block: suspend () -> T): T {
        val trace = currentTraceContext()
        val span = tracer.startSpan(name = "$TAG.$operation", traceId = trace.traceId ?: UNTRACED)
        return try {
            block()
        } finally {
            tracer.endSpan(span)
            logger.debug(
                TAG,
                "$operation.done",
                tc = trace.toLog(),
                fields = mapOf(Key.RESOURCE to resource),
            )
        }
    }

    /** A request the server answered, but not with success. The status is the actionable part. */
    suspend fun rejected(operation: String, resource: String, status: Int) {
        logger.error(
            TAG,
            "$operation.rejected",
            tc = currentTraceContext().toLog(),
            fields = mapOf(Key.RESOURCE to resource, Key.STATUS to status),
        )
    }

    /**
     * A request that never got an answer — a timeout, a dropped connection, a DNS failure.
     * Recorded as a non-fatal too, because the caller turns it into a retry and it would
     * otherwise never reach a dashboard.
     */
    suspend fun failed(operation: String, resource: String, throwable: Throwable) {
        crash.recordNonFatal(
            throwable,
            mapOf(Key.OPERATION to operation, Key.RESOURCE to resource),
        )
        logger.error(
            TAG,
            "$operation.failed",
            tc = currentTraceContext().toLog(),
            fields = mapOf(Key.RESOURCE to resource, Key.ERROR to throwable::class.simpleName),
        )
    }

    /**
     * A retried attempt. Counted rather than silent: a call that succeeds on the third try is
     * a healthy dashboard and an unhealthy network, and only this tells the two apart.
     *
     * Not suspend, unlike its siblings — Ktor's retry hook is a plain lambda, so this cannot
     * read the coroutine's trace. It logs without one instead of forcing a signature change,
     * because instrumentation never dictates the shape of the thing it observes.
     */
    fun retried(resource: String, attempt: Int) {
        logger.warn(TAG, "request.retried", fields = mapOf(Key.RESOURCE to resource, Key.ATTEMPT to attempt))
    }

    /** How many rows a call actually moved. The number that says whether sync is working. */
    suspend fun rows(operation: String, resource: String, count: Int) {
        logger.info(
            TAG,
            "$operation.rows",
            tc = currentTraceContext().toLog(),
            fields = mapOf(Key.RESOURCE to resource, Key.COUNT to count),
        )
    }

    /**
     * The graph resolved without credentials, so the fakes are in place.
     *
     * Logged once at startup because the alternative is a developer watching sync "work"
     * perfectly against a server that was never contacted.
     */
    fun notConfigured() {
        logger.warn(TAG, "not.configured")
    }

    /** Bridges the performance module's coroutine-carried trace to the logging module's. */
    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    /** Field keys — kept here so a dashboard query never breaks on a renamed literal. */
    internal object Key {
        const val RESOURCE = "resource"
        const val OPERATION = "operation"
        const val STATUS = "status"
        const val ERROR = "error"
        const val ATTEMPT = "attempt"
        const val COUNT = "count"
    }

    internal companion object {
        const val TAG = "supabase"

        /** Span traceId when no caller installed a trace — grouped rather than dropped. */
        const val UNTRACED = "untraced"

        /* Operation names used as the first half of every span/event name. */
        const val SELECT = "select"
        const val UPSERT = "upsert"
        const val RPC = "rpc"
        const val UPLOAD = "upload"
        const val DOWNLOAD = "download"
        const val SIGN = "sign"
        const val REMOVE = "remove"
    }
}
