package com.hopcape.logging.api

/**
 * A logger scoped to a fixed [tag] and/or [TraceContext]. Returned by
 * [HLogger.tag] so call sites don't repeat the tag/trace on every call.
 *
 * Implements the same [Logger] contract (LSP) — a ScopedLogger is substitutable
 * anywhere a [Logger] is expected. The constructor is `internal`: callers obtain
 * instances only through the facade, never by construction.
 */
@StableLoggerApi
class ScopedLogger internal constructor(
    private val delegate: Logger,
    private val tag: String,
    private val traceContext: TraceContext?
) : Logger {

    override fun log(
        level: LogLevel,
        tag: String,
        event: String,
        traceContext: TraceContext?,
        fields: Map<String, Any?>
    ) {
        delegate.log(level, tag, event, traceContext ?: this.traceContext, fields)
    }

    /** Convenience overload — uses the tag this scope was created with. */
    fun log(level: LogLevel, event: String, fields: Map<String, Any?> = emptyMap()) {
        log(level, tag, event, traceContext, fields)
    }

    fun d(event: String, fields: Map<String, Any?> = emptyMap()) = log(LogLevel.DEBUG, event, fields)
    fun i(event: String, fields: Map<String, Any?> = emptyMap()) = log(LogLevel.INFO, event, fields)
    fun w(event: String, fields: Map<String, Any?> = emptyMap()) = log(LogLevel.WARN, event, fields)
    fun e(event: String, fields: Map<String, Any?> = emptyMap()) = log(LogLevel.ERROR, event, fields)

    /** Derive a further-scoped child with a narrower trace (e.g. per-attempt traceId). */
    fun withTrace(traceId: String): ScopedLogger =
        ScopedLogger(delegate, tag, (traceContext ?: TraceContext()).withNewTrace(traceId))

    override fun flush() = delegate.flush()
}
