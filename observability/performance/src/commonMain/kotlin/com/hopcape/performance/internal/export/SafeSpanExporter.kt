package com.hopcape.performance.internal.export

import com.hopcape.performance.internal.model.CompletedSpan

// ─────────────────────────────────────────────────────────────
// SafeSpanExporter — Decorator that exception-isolates a delegate
// exporter so one failing backend can never crash the app. But note
// the asymmetry with the analytics SafeDestination: here export()
// RE-THROWS after reporting, because the dispatcher needs the
// failure signal to decide whether to retry / dead-letter the span.
// flush() swallows, since a flush failure is not worth retrying.
// ─────────────────────────────────────────────────────────────
internal class SafeSpanExporter(
    private val delegate: SpanExporter,
    private val onError: (name: String, error: Throwable) -> Unit,
) : SpanExporter {

    override val name: String get() = delegate.name

    override fun export(span: CompletedSpan) {
        try {
            delegate.export(span)
        } catch (t: Throwable) {
            onError(delegate.name, t)
            throw t // surface to the dispatcher so it can retry / dead-letter
        }
    }

    override fun flush() {
        try {
            delegate.flush()
        } catch (t: Throwable) {
            onError(delegate.name, t)
        }
    }
}
