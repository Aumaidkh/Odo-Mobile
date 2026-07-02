package com.hopcape.logging.internal.sinks

import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.internal.model.LogEvent

internal class FileSink(
    private val filePath: String,
    private val minLevel: LogLevel = LogLevel.DEBUG
) : LogSink {
    // In production: buffered/async writer + rotation policy.
    // Kept minimal here to focus on the pattern, not I/O plumbing.
    override fun write(event: LogEvent) {
        if (event.level.priority < minLevel.priority) return
        val line = toJsonLine(event)
        println("Append log to file yet to be implemented")
        //File(filePath).appendText(line + "\n")
    }

    private fun toJsonLine(e: LogEvent): String {
        val base = linkedMapOf<String, Any?>(
            "ts" to e.timestampMs,
            "level" to e.level.name,
            "tag" to e.tag,
            "event" to e.event,
            "sessionId" to e.sessionId,
            "flowId" to e.flowId,
            "traceId" to e.traceId
        )
        base.putAll(e.fields)
        return base.entries.joinToString(",", prefix = "{", postfix = "}") { (k, v) ->
            "\"$k\":${quoteIfString(v)}"
        }
    }

    private fun quoteIfString(v: Any?): String = when (v) {
        null -> "null"
        is Number, is Boolean -> v.toString()
        else -> "\"$v\""
    }
}