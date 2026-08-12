package com.hopcape.logging.internal

import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.TraceContext
import com.hopcape.logging.internal.model.LogEvent
import com.hopcape.logging.internal.sinks.LogSink

// ─────────────────────────────────────────────────────────────
// LoggerImpl — SRP: its only job is "route a LogEvent to every
// configured sink". It knows nothing about Logcat/File/Remote
// specifics, and nothing about PII rules — those live in the
// sinks/decorators injected into it (DIP).
//
// LSP holds: any List<LogSink> combination works here, including
// ones wrapped in RedactingSink — LoggerImpl treats them identically.
// ─────────────────────────────────────────────────────────────
internal class LoggerImpl(
    private val sinks: List<LogSink>
) : Logger {

    override fun log(
        level: LogLevel,
        tag: String,
        event: String,
        traceContext: TraceContext?,
        fields: Map<String, Any?>
    ) {
        val builder = LogEvent.Builder(tag, event)
            .level(level)
            .fields(fields)

        traceContext?.let {
            builder.sessionId(it.sessionId)
            builder.flowId(it.flowId)
            builder.traceId(it.traceId)
        }

        val logEvent = builder.build()
        sinks.forEach { sink -> sink.write(logEvent) }
    }

    override fun flush() {
        sinks.forEach { it.flush() }
    }
}