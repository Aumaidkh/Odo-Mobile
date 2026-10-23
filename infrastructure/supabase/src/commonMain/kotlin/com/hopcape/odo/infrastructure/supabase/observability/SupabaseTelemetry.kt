package com.hopcape.odo.infrastructure.supabase.observability

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.Logger
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.currentTraceContext
import kotlinx.io.IOException
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
    private val analytics: AnalyticsTracker,
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

    /**
     * A request the server answered, but not with success.
     *
     * [cause] carries the SQLSTATE and the violated constraint's name when the server sent
     * them. Both are schema facts, so they are safe to log where the error body is not: a
     * status alone cannot tell a duplicate plate from a bad enum value from a rejected
     * policy, and those have nothing in common to fix.
     */
    suspend fun rejected(operation: String, resource: String, status: Int, cause: String? = null) {
        logger.error(
            TAG,
            "$operation.rejected",
            tc = currentTraceContext().toLog(),
            fields = buildMap {
                put(Key.RESOURCE, resource)
                put(Key.STATUS, status)
                cause?.let { put(Key.CAUSE, it) }
            },
        )
    }

    /**
     * A request that never got an answer — a timeout, a dropped connection, a DNS failure.
     *
     * A server that could not be reached is counted, not reported: Odo is offline-first, so a
     * phone with no network is the expected state. Anything else still gets a non-fatal.
     */
    suspend fun failed(operation: String, resource: String, throwable: Throwable) {
        if (throwable.isUnreachable()) {
            analytics.track(
                EVENT_UNREACHABLE,
                mapOf(Key.RESOURCE to resource, Key.ERROR to throwable::class.simpleName),
            )
        } else {
            crash.recordNonFatal(
                throwable,
                mapOf(Key.OPERATION to operation, Key.RESOURCE to resource),
            )
        }
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

    /**
     * The device heartbeat went out without an analytics id.
     *
     * Warned, not counted: the id is the whole reason the row exists, and if Firebase never
     * hands one over the feature is useless while looking perfectly healthy.
     */
    suspend fun deviceMissingAnalyticsId() {
        logger.warn(
            TAG,
            "device.no_analytics_id",
            tc = currentTraceContext().toLog(),
        )
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

    /**
     * Sign-in was asked for on a build with no credentials.
     *
     * Separate from [notConfigured], which fires once at startup: this one names the moment
     * an owner actually tried, which is the line that says why the screen refused.
     */
    fun signInUnavailable() {
        logger.warn(TAG, "auth.unavailable")
    }

    /** Bridges the performance module's coroutine-carried trace to the logging module's. */
    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    /**
     * Whether the server was never reached, as opposed to reached and disagreed with.
     *
     * One check covers the lot: Ktor's timeout exceptions all extend `IOException`, as do the
     * platform's DNS and connection failures.
     */
    private fun Throwable.isUnreachable(): Boolean = this is IOException

    /** Field keys — kept here so a dashboard query never breaks on a renamed literal. */
    internal object Key {
        const val RESOURCE = "resource"
        const val OPERATION = "operation"
        const val STATUS = "status"
        const val CAUSE = "cause"
        const val ERROR = "error"
        const val ATTEMPT = "attempt"
        const val COUNT = "count"
    }

    internal companion object {
        const val TAG = "supabase"

        /** A request that got no reply. Counted, because being offline is not a bug. */
        const val EVENT_UNREACHABLE = "request_unreachable"

        /** Span traceId when no caller installed a trace — grouped rather than dropped. */
        const val UNTRACED = "untraced"

        /* Operation names used as the first half of every span/event name. */
        const val SELECT = "select"
        const val UPSERT = "upsert"
        const val INSERT = "insert"
        const val UPDATE = "update"
        const val RPC = "rpc"
        const val UPLOAD = "upload"
        const val DOWNLOAD = "download"
        const val SIGN = "sign"
        const val REMOVE = "remove"
    }
}
