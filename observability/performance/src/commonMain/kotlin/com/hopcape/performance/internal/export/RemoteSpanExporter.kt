package com.hopcape.performance.internal.export

import com.hopcape.performance.internal.model.CompletedSpan

// ─────────────────────────────────────────────────────────────
// RemoteSpanExporter — the primary production exporter: ships spans
// to the APM backend (an OTLP/HTTP collector or a vendor SDK). It is
// a stub here (the network client is wired in a later slice), but it
// occupies the seam so the pipeline shape is complete and the
// batch/retry/dead-letter machinery has a real target to exercise —
// exactly how the analytics module stubs PostHog/Firebase.
// ─────────────────────────────────────────────────────────────
internal class RemoteSpanExporter(
    private val onExport: (CompletedSpan) -> Unit = {},
) : SpanExporter {

    override val name: String = "remote"

    override fun export(span: CompletedSpan) {
        // TODO(perf): POST the span batch to the OTLP collector via Ktor.
        onExport(span)
    }

    override fun flush() {
        // TODO(perf): force-drain any in-flight HTTP batch.
    }
}
