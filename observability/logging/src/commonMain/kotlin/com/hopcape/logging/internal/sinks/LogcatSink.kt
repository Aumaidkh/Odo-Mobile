package com.hopcape.logging.internal.sinks

import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.internal.model.LogEvent

// ─────────────────────────────────────────────────────────────
// Concrete sinks — each does exactly one thing (SRP)
// ─────────────────────────────────────────────────────────────
internal class LogcatSink(private val minLevel: LogLevel = LogLevel.VERBOSE) : LogSink {
    override fun write(event: LogEvent) {
        if (event.level.priority < minLevel.priority) return
        println(formatForConsole(event))
    }

    private fun formatForConsole(e: LogEvent): String {
        val ctx = listOfNotNull(
            e.sessionId?.let { "session=$it" },
            e.flowId?.let { "flow=$it" },
            e.traceId?.let { "trace=$it" }
        ).joinToString(" ")
        return "[${e.level}] ${e.tag}: ${e.event} | $ctx | ${e.fields}"
    }
}