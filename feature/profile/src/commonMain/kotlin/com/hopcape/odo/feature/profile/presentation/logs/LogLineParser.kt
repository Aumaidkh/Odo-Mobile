package com.hopcape.odo.feature.profile.presentation.logs

import com.hopcape.logging.api.LogLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Turns the file logger's NDJSON (`FileSink.toJsonLine`) back into [LogEntry]s.
 *
 * A line that fails to parse — a torn write mid-append, a stray byte the active file
 * happened to be read mid-write — is **skipped, not fatal**, the same rule
 * `LogFileNaming.parseOpenedAtMs` already follows for a malformed filename: a debug screen
 * must not crash on the exact artifact it exists to show you.
 */
internal object LogLineParser {

    fun parseLines(text: String): List<LogEntry> =
        text.lineSequence().mapNotNull(::parseLine).toList()

    private fun parseLine(line: String): LogEntry? {
        if (line.isBlank()) return null
        return runCatching {
            val json = Json.parseToJsonElement(line).jsonObject
            val timestampMs = json["ts"]?.jsonPrimitive?.longOrNull ?: return null
            val levelName = json["level"]?.jsonPrimitive?.contentOrNull ?: return null
            val tag = json["tag"]?.jsonPrimitive?.contentOrNull ?: return null
            val message = json["event"]?.jsonPrimitive?.contentOrNull ?: return null
            LogEntry(timestampMs, LogLevel.valueOf(levelName), tag, message)
        }.getOrNull()
    }
}
