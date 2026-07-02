package com.hopcape.logging.internal

import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.LoggerConfig
import com.hopcape.logging.api.TraceContext
import com.hopcape.logging.internal.redactor.PiiRedactor
import com.hopcape.logging.internal.redactor.RegexPiiRedactor
import com.hopcape.logging.internal.sinks.FileSink
import com.hopcape.logging.internal.sinks.LogcatSink
import com.hopcape.logging.internal.sinks.RedactingSink
import com.hopcape.logging.internal.sinks.SafeSink

// ─────────────────────────────────────────────────────────────
// LoggerFactory — Factory + composition root for the sink graph.
// The ONE place that knows concrete types (LogcatSink, FileSink,
// RedactingSink, SafeSink, ...); everything else depends only on
// the `Logger` interface (DIP).
//
// It is `internal`: the module's public entry points are the Koin
// `loggingModule(isDebug)` and the `HLogger` facade — both feed a
// `LoggerConfig` through here, so sink wiring lives in exactly one place.
// ─────────────────────────────────────────────────────────────
internal object LoggerFactory {

    /**
     * Builds a [Logger] from [config]: a Logcat sink plus an optional file sink,
     * each optionally PII-redacted and always wrapped in a [SafeSink] so a
     * misbehaving sink can never crash a caller.
     */
    fun create(config: LoggerConfig): Logger {
        val redactor: PiiRedactor? =
            if (config.piiRedactionEnabled) RegexPiiRedactor() else null

        val rawSinks = buildList {
            add(LogcatSink(minLevel = config.minLevel))
            config.filePath?.let { add(FileSink(it, minLevel = config.minLevel)) }
            // config.remoteEndpoint -> reserved for a future RemoteSink.
        }

        val safeSinks = rawSinks.map { sink ->
            val delegate = if (redactor != null) RedactingSink(sink, redactor) else sink
            // Logging is strictly additive — swallow sink failures.
            SafeSink(delegate) { }
        }

        return LoggerImpl(safeSinks)
    }

    // Useful for unit tests and as the pre-init fallback — a no-op logger
    // satisfies the same interface (LSP).
    fun createNoOpLogger(): Logger = object : Logger {
        override fun log(level: LogLevel, tag: String, event: String, traceContext: TraceContext?, fields: Map<String, Any?>) {}
        override fun flush() {}
    }
}
