package com.hopcape.logging.internal.sinks

import com.hopcape.logging.internal.model.LogEvent

// ─────────────────────────────────────────────────────────────
// LogSink — Strategy pattern. Each sink knows only how to
// persist/emit a LogEvent somewhere. LoggerImpl doesn't care
// which sinks it has (DIP) — it just calls write() on all of them.
//
// OCP in action: adding a new destination (Sentry, Firebase,
// Elastic) = new class implementing LogSink. Zero changes to
// existing sinks or to LoggerImpl.
// ─────────────────────────────────────────────────────────────
internal interface LogSink {
    fun write(event: LogEvent)
    fun flush() {}
}