package com.hopcape.logging.internal.sinks

import com.hopcape.logging.internal.model.LogEvent

// ─────────────────────────────────────────────────────────────
// SafeSink — Decorator that guarantees a misbehaving sink
// (disk full, network exception, OOM in a serializer) NEVER
// propagates an exception into caller code. This is the single
// most important guarantee of an enterprise logging API: logging
// must be strictly additive, never a new crash source.
// ─────────────────────────────────────────────────────────────
internal class SafeSink(
    private val delegate: LogSink,
    private val onInternalError: (Throwable) -> Unit
) : LogSink {
    override fun write(event: LogEvent) {
        try {
            delegate.write(event)
        } catch (t: Throwable) {
            onInternalError(t)
        }
    }

    override fun flush() {
        try {
            delegate.flush()
        } catch (t: Throwable) {
            onInternalError(t)
        }
    }
}