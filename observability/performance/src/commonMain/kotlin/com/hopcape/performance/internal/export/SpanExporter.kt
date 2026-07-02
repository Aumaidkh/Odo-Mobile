package com.hopcape.performance.internal.export

import com.hopcape.performance.internal.model.CompletedSpan

// ─────────────────────────────────────────────────────────────
// SpanExporter — a delivery target for finished spans (an APM
// backend, a console, etc.). The analog of the analytics module's
// AnalyticsDestination. Concrete exporters stay `internal`; the
// dispatcher fans a batch out to every registered exporter and
// retries the ones that throw.
// ─────────────────────────────────────────────────────────────
internal interface SpanExporter {
    /** Stable name for diagnostics/dead-letter messages. */
    val name: String

    /** Delivers one span. May throw — the dispatcher isolates and retries failures. */
    fun export(span: CompletedSpan)

    /** Asks the exporter to persist/flush anything it has buffered. */
    fun flush()
}
