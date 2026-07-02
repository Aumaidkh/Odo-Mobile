package com.hopcape.logging.api

// ─────────────────────────────────────────────────────────────
// Logger — minimal public contract (ISP).
// Callers (BLE code, ViewModels, repositories) only ever see this.
// They don't know or care how many sinks exist, or how redaction
// works — that's an implementation detail hidden behind DIP.
// ─────────────────────────────────────────────────────────────
interface Logger {
    fun log(
        level: LogLevel,
        tag: String,
        event: String,
        traceContext: TraceContext? = null,
        fields: Map<String, Any?> = emptyMap()
    )

    fun verbose(tag: String, event: String, tc: TraceContext? = null, fields: Map<String, Any?> = emptyMap()) =
        log(LogLevel.VERBOSE, tag, event, tc, fields)

    fun debug(tag: String, event: String, tc: TraceContext? = null, fields: Map<String, Any?> = emptyMap()) =
        log(LogLevel.DEBUG, tag, event, tc, fields)

    fun info(tag: String, event: String, tc: TraceContext? = null, fields: Map<String, Any?> = emptyMap()) =
        log(LogLevel.INFO, tag, event, tc, fields)

    fun warn(tag: String, event: String, tc: TraceContext? = null, fields: Map<String, Any?> = emptyMap()) =
        log(LogLevel.WARN, tag, event, tc, fields)

    fun error(tag: String, event: String, tc: TraceContext? = null, fields: Map<String, Any?> = emptyMap()) =
        log(LogLevel.ERROR, tag, event, tc, fields)

    fun flush()
}