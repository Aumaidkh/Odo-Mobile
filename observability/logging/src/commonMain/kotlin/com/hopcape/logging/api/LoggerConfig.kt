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
    val piiRedactionEnabled: Boolean = true
) {
    enum class Environment { DEBUG, STAGING, PRODUCTION }

    class Builder {
        private var environment: Environment = Environment.PRODUCTION
        private var filePath: String? = null
        private var remoteEndpoint: String? = null
        private var minLevel: LogLevel = LogLevel.INFO
        private var piiRedactionEnabled: Boolean = true

        fun environment(env: Environment) = apply { this.environment = env }
        fun filePath(path: String) = apply { this.filePath = path }
        fun remoteEndpoint(url: String) = apply { this.remoteEndpoint = url }
        fun minLevel(level: LogLevel) = apply { this.minLevel = level }
        fun piiRedaction(enabled: Boolean) = apply { this.piiRedactionEnabled = enabled }

        fun build(): LoggerConfig = LoggerConfig(
            environment, filePath, remoteEndpoint, minLevel, piiRedactionEnabled
        )
    }
}

/** Kotlin DSL sugar over [LoggerConfig.Builder] for call-site readability. */
@StableLoggerApi
inline fun loggerConfig(block: LoggerConfig.Builder.() -> Unit): LoggerConfig =
    LoggerConfig.Builder().apply(block).build()