package com.hopcape.logging.internal

import com.hopcape.logging.api.FileLoggingConfig
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.LoggerConfig
import com.hopcape.logging.api.TraceContext
import com.hopcape.logging.internal.file.LogRetentionPruner
import com.hopcape.logging.internal.file.RotationPolicy
import com.hopcape.logging.internal.redactor.PiiRedactor
import com.hopcape.logging.internal.redactor.RegexPiiRedactor
import com.hopcape.logging.internal.sinks.AsyncSink
import com.hopcape.logging.internal.sinks.FileSink
import com.hopcape.logging.internal.sinks.LogSink
import com.hopcape.logging.internal.sinks.LogcatSink
import com.hopcape.logging.internal.sinks.RedactingSink
import com.hopcape.logging.internal.sinks.SafeSink
import kotlin.time.Clock

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
     * Builds a [Logger] from [config]: a Logcat sink plus an optional file branch
     * (`AsyncSink` over `FileSink`, built when [LoggerConfig.fileLogging] is set — see
     * [buildFileSink]), each optionally PII-redacted and always wrapped in a [SafeSink]
     * so a misbehaving sink can never crash a caller.
     */
    fun create(config: LoggerConfig): Logger {
        val redactor: PiiRedactor? =
            if (config.piiRedactionEnabled) RegexPiiRedactor() else null

        val rawSinks = buildList {
            add(LogcatSink(minLevel = config.minLevel))
            config.fileLogging?.let { add(buildFileSink(it, config.minLevel, config.onInternalError)) }
            // config.remoteEndpoint -> reserved for a future RemoteSink.
        }

        val safeSinks = rawSinks.map { sink ->
            val delegate = if (redactor != null) RedactingSink(sink, redactor) else sink
            // Logging is strictly additive — a sink never throws into a caller — but the
            // failure itself must still go somewhere (docs/LOGGING_PLAN.md §8), so it is
            // handed to whatever the app wired up rather than swallowed here.
            SafeSink(delegate, config.onInternalError)
        }

        return LoggerImpl(safeSinks)
    }

    /**
     * `AsyncSink(FileSink(...))` — batching sits between redaction and the file so a
     * redacted event never touches disk on the caller's thread (docs/LOGGING_PLAN.md §4).
     *
     * Seals any `.active` file [FileLoggingConfig.store] is still holding from a process
     * that died without sealing it, before the new session's [FileSink] writes anything —
     * this, not a per-event policy, is what makes "a new file every cold start" (§6.3) true.
     * Guarded: this runs during [HLogger.init], typically from `Application.onCreate` — a
     * disk error while scanning for orphans must not crash app startup.
     */
    private fun buildFileSink(
        fileLogging: FileLoggingConfig,
        minLevel: LogLevel,
        onInternalError: (Throwable) -> Unit,
    ): LogSink {
        val nowMs = { Clock.System.now().toEpochMilliseconds() }
        try {
            fileLogging.store.sealOrphans()
        } catch (t: Throwable) {
            onInternalError(t)
        }

        val policies = buildList {
            add(RotationPolicy.maxSize(fileLogging.maxActiveFileBytes))
            if (fileLogging.rotateAtUtcMidnight) add(RotationPolicy.utcMidnight())
        }
        val pruner = LogRetentionPruner(fileLogging.store, fileLogging.retention, nowMs)
        val fileSink = FileSink(
            store = fileLogging.store,
            rotation = RotationPolicy.anyOf(*policies.toTypedArray()),
            retentionPruner = pruner,
            nowMs = nowMs,
            minLevel = minLevel,
        )
        return AsyncSink(fileSink, onInternalError = onInternalError)
    }

    // Useful for unit tests and as the pre-init fallback — a no-op logger
    // satisfies the same interface (LSP).
    fun createNoOpLogger(): Logger = object : Logger {
        override fun log(level: LogLevel, tag: String, event: String, traceContext: TraceContext?, fields: Map<String, Any?>) {}
        override fun flush() {}
    }
}
