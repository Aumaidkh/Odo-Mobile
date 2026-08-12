package com.hopcape.logging.api

/**
 * Marks a stable, supported public API. Anything NOT marked with
 * this (or explicitly `internal`) should be treated as subject to
 * change without notice — mirrors how kotlinx marks experimental
 * surfaces, but inverted: this marks what's SAFE to depend on.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class StableLoggerApi

/**
 * Configuration for [HLogger]. Immutable once built — construct
 * via [LoggerConfig.Builder] or the [loggerConfig] DSL helper.
 */
@StableLoggerApi
data class LoggerConfig(
    val environment: Environment,
    val filePath: String? = null,
    val remoteEndpoint: String? = null,
    val minLevel: LogLevel = LogLevel.INFO,
    val piiRedactionEnabled: Boolean = true,
    /**
     * Enables the durable file sink (docs/LOGGING_PLAN.md). `null` — the default —
     * means no file sink is built, same as an unset [filePath] always meant.
     *
     * [filePath] alone can no longer build one: this module cannot turn a bare path
     * into real disk storage (the on-disk `LogFileStore` lives in `:core:platform`,
     * which depends on this module and not the reverse — see [FileLoggingConfig]). It
     * never actually did before this either — the old file sink only ever printed a
     * placeholder — so a build with [filePath] set and [fileLogging] unset keeps
     * writing zero real bytes, exactly as it always has. [filePath] is kept only so
     * existing construction sites keep compiling.
     */
    val fileLogging: FileLoggingConfig? = null,
    /**
     * Called when a sink throws — `SafeSink` catches it so logging stays strictly additive,
     * but the failure must not just vanish (docs/LOGGING_PLAN.md §8: "the logger must not
     * report its own failures through itself"). The default no-op matches this module's
     * prior behavior; the app wires this to `CrashRecorder.recordNonFatal` so a sink that
     * starts failing in production — a full disk, a permission error — is something the
     * team would actually find out about.
     */
    val onInternalError: (Throwable) -> Unit = {},
) {
    enum class Environment { DEBUG, STAGING, PRODUCTION }

    class Builder {
        private var environment: Environment = Environment.PRODUCTION
        private var filePath: String? = null
        private var remoteEndpoint: String? = null
        private var minLevel: LogLevel = LogLevel.INFO
        private var piiRedactionEnabled: Boolean = true
        private var fileLogging: FileLoggingConfig? = null
        private var onInternalError: (Throwable) -> Unit = {}

        fun environment(env: Environment) = apply { this.environment = env }
        fun filePath(path: String) = apply { this.filePath = path }
        fun remoteEndpoint(url: String) = apply { this.remoteEndpoint = url }
        fun minLevel(level: LogLevel) = apply { this.minLevel = level }
        fun piiRedaction(enabled: Boolean) = apply { this.piiRedactionEnabled = enabled }
        fun fileLogging(config: FileLoggingConfig) = apply { this.fileLogging = config }
        fun onInternalError(fn: (Throwable) -> Unit) = apply { this.onInternalError = fn }

        fun build(): LoggerConfig = LoggerConfig(
            environment, filePath, remoteEndpoint, minLevel, piiRedactionEnabled, fileLogging, onInternalError
        )
    }
}

/** Kotlin DSL sugar over [LoggerConfig.Builder] for call-site readability. */
@StableLoggerApi
inline fun loggerConfig(block: LoggerConfig.Builder.() -> Unit): LoggerConfig =
    LoggerConfig.Builder().apply(block).build()