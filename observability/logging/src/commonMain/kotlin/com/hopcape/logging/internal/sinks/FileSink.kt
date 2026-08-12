package com.hopcape.logging.internal.sinks

import com.hopcape.logging.api.LogFileStats
import com.hopcape.logging.api.LogFileStore
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.internal.file.LogRetentionPruner
import com.hopcape.logging.internal.file.RotationPolicy
import com.hopcape.logging.internal.model.LogEvent

/**
 * Appends events to the durable [store] as NDJSON, one line per event, rolling to a new file
 * per [rotation] and pruning old ones per [retentionPruner]. The leaf of the file branch of
 * the sink chain — everything above it (`SafeSink`, `RedactingSink`, `AsyncSink`) has already
 * run, so this only ever sees one caller at a time and never needs its own locking.
 *
 * Implements [Sealable] so `AsyncSink` can finalize the current file on an explicit
 * `flush()` — this is what lets the upload coordinator (L6) get an accurate, uploadable
 * file for whatever has been logged so far, sealed with THIS class's own live counters
 * rather than a guessed or zeroed [LogFileStats].
 */
internal class FileSink(
    private val store: LogFileStore,
    private val rotation: RotationPolicy,
    private val retentionPruner: LogRetentionPruner,
    private val nowMs: () -> Long,
    private val minLevel: LogLevel = LogLevel.DEBUG,
) : LogSink, Sealable {

    private var openedAtMs: Long? = null
    private var sizeBytes = 0L
    private var lineCount = 0
    private var warnCount = 0
    private var errorCount = 0
    private var hadFatal = false

    override fun write(event: LogEvent) {
        if (event.level.priority < minLevel.priority) return

        val now = nowMs()
        openedAtMs?.let { opened ->
            if (rotation.shouldRotate(opened, sizeBytes, now)) sealCurrent()
        }
        if (openedAtMs == null) openedAtMs = now

        val line = toJsonLine(event)
        store.appendToActive(listOf(line))
        recordWrite(event.level, line)
    }

    private fun recordWrite(level: LogLevel, line: String) {
        // +1 for the newline appendToActive implicitly adds between lines.
        sizeBytes += line.encodeToByteArray().size + 1
        lineCount++
        when (level) {
            LogLevel.WARN -> warnCount++
            LogLevel.ERROR -> errorCount++
            LogLevel.FATAL -> hadFatal = true
            else -> {}
        }
    }

    override fun sealCurrentFile() {
        if (openedAtMs != null) sealCurrent()
    }

    private fun sealCurrent() {
        val handle = store.sealActive(currentStats())
        resetCounters()
        if (handle != null) retentionPruner.prune()
    }

    private fun currentStats() = LogFileStats(lineCount, warnCount, errorCount, hadFatal)

    private fun resetCounters() {
        openedAtMs = null
        sizeBytes = 0L
        lineCount = 0
        warnCount = 0
        errorCount = 0
        hadFatal = false
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
