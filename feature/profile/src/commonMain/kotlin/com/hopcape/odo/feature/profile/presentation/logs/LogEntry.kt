package com.hopcape.odo.feature.profile.presentation.logs

import com.hopcape.logging.api.LogLevel

/**
 * One parsed line from the file logger's NDJSON output — just enough to render a Logcat-like
 * row and filter it. [FileSink][com.hopcape.logging.internal.sinks.FileSink] writes more
 * (`sessionId`, `flowId`, `traceId`, arbitrary extra fields); this screen doesn't show them,
 * so [LogLineParser] doesn't keep them either.
 */
internal data class LogEntry(
    val timestampMs: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
)
