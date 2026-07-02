package com.hopcape.logging

import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.logging.internal.model.LogEvent
import com.hopcape.logging.internal.sinks.LogSink

/**
 * Test doubles shared across the logging test suite. They implement the module's
 * (internal) `LogSink` and public `Logger` ports so the real components can be
 * exercised without touching Logcat/files — the whole point of the port design.
 */

/** A [LogSink] that records every event and flush it receives. */
internal class RecordingSink : LogSink {
    val written = mutableListOf<LogEvent>()
    var flushCount = 0
        private set

    override fun write(event: LogEvent) {
        written += event
    }

    override fun flush() {
        flushCount++
    }
}

/** A [LogSink] that always throws — to prove `SafeSink` isolates failures. */
internal class ThrowingSink(
    private val boom: Throwable = RuntimeException("sink boom"),
) : LogSink {
    override fun write(event: LogEvent): Unit = throw boom
    override fun flush(): Unit = throw boom
}

/** A [Logger] that records every call — for testing `ScopedLogger` in isolation. */
internal class RecordingLogger : Logger {
    data class Entry(
        val level: LogLevel,
        val tag: String,
        val event: String,
        val traceContext: TraceContext?,
        val fields: Map<String, Any?>,
    )

    val entries = mutableListOf<Entry>()
    var flushCount = 0
        private set

    val last: Entry get() = entries.last()

    override fun log(
        level: LogLevel,
        tag: String,
        event: String,
        traceContext: TraceContext?,
        fields: Map<String, Any?>,
    ) {
        entries += Entry(level, tag, event, traceContext, fields)
    }

    override fun flush() {
        flushCount++
    }
}
