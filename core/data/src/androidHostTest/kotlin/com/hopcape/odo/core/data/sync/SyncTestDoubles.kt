package com.hopcape.odo.core.data.sync

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span

/**
 * Shared scaffolding for the sync tests that still live here — just [BlobUploaderTest],
 * since every other sync test now runs against the real database in
 * `:infrastructure:database`, which carries its own copy of this file.
 */
internal fun silentDataTelemetry() =
    DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = NoopCrash)

internal object NoopLogger : Logger {
    override fun log(
        level: LogLevel,
        tag: String,
        event: String,
        traceContext: TraceContext?,
        fields: Map<String, Any?>,
    ) = Unit

    override fun flush() = Unit
}

internal object NoopTracer : PerformanceTracer {
    override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
        object : Span {
            override val spanId = "span"
            override val traceId = traceId
            override val parentSpanId = parentSpanId
            override val name = name
            override fun setAttribute(key: String, value: Any?): Span = this
        }

    override fun endSpan(span: Span) = Unit
    override fun flush() = Unit
}

internal object NoopCrash : CrashRecorder {
    override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) = Unit
    override fun leaveBreadcrumb(tag: String, message: String) = Unit
    override fun setCustomKey(key: String, value: Any?) = Unit
    override fun setUserId(userId: String?) = Unit
}
