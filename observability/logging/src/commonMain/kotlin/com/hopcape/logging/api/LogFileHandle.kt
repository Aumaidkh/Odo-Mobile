package com.hopcape.logging.api

/** A sealed log file — a `LogFileStore.sealActive`/`sealOrphans` result, or a `listSealed` entry. */
@StableLoggerApi
data class LogFileHandle(
    val name: String,
    val sizeBytes: Long,
    val openedAtMs: Long,
    val sealedAtMs: Long,
    /** Null when this file was recovered from a killed process — the live counters that
     *  would have produced it never ran. Null must not be read as "no warnings/errors". */
    val stats: LogFileStats?,
)

/** Per-file counters a [com.hopcape.logging.internal.sinks.FileSink] keeps while it writes,
 *  handed to `LogFileStore.sealActive` so a caller can triage a file without opening it. */
@StableLoggerApi
data class LogFileStats(
    val lineCount: Int,
    val warnCount: Int,
    val errorCount: Int,
    val hadFatal: Boolean,
)
