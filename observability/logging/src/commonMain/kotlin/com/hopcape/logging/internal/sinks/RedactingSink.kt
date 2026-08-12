package com.hopcape.logging.internal.sinks

import com.hopcape.logging.internal.model.LogEvent
import com.hopcape.logging.internal.redactor.PiiRedactor

// ─────────────────────────────────────────────────────────────
// RedactingSink — Decorator pattern.
// Wraps ANY LogSink and scrubs PII before delegating to it.
// This is the key OCP/DIP move: we don't modify LogcatSink,
// FileSink, or RemoteSink to add redaction — we wrap them.
// ─────────────────────────────────────────────────────────────
internal class RedactingSink(
    private val delegate: LogSink,
    private val redactor: PiiRedactor
) : LogSink {
    override fun write(event: LogEvent) {
        delegate.write(redactor.redact(event))
    }

    override fun flush() = delegate.flush()
}