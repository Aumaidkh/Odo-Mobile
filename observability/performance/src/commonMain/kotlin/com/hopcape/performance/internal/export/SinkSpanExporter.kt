package com.hopcape.performance.internal.export

import com.hopcape.performance.api.SpanSink
import com.hopcape.performance.internal.model.CompletedSpan
import com.hopcape.performance.internal.redactor.RegexSpanPiiRedactor
import com.hopcape.performance.internal.redactor.SpanPiiRedactor

// ─────────────────────────────────────────────────────────────
// SinkSpanExporter — Adapter from the public SpanSink port to the
// internal SpanExporter pipeline shape, so an external sink is
// wrapped in SafeSpanExporter exactly like a built-in exporter. Keeps
// CompletedSpan internal: a sink only ever sees the resolved
// name/ids/timing/attributes it needs, never the span model.
//
// Every attribute value is redacted here, once, before it reaches
// ANY sink — this is the module's only egress point to a vendor, so
// it is the one place PII scrubbing has to happen (see CLAUDE.md's
// "never log PII" rule).
//
// SpanSink.export returns Boolean; SpanExporter.export signals
// failure by throwing. `false` becomes a throw so the dispatcher's
// existing retry/dead-letter path keeps working unchanged.
// ─────────────────────────────────────────────────────────────
internal class SinkSpanExporter(
    private val sink: SpanSink,
    private val redactor: SpanPiiRedactor = RegexSpanPiiRedactor(),
) : SpanExporter {

    override val name: String = sink.name

    override fun export(span: CompletedSpan) {
        val accepted = sink.export(
            name = span.name,
            traceId = span.traceId,
            spanId = span.spanId,
            parentSpanId = span.parentSpanId,
            startEpochMs = span.startEpochMs,
            durationMs = span.durationMs,
            isError = span.isError,
            attributes = redactedAttributes(span),
        )
        if (!accepted) throw IllegalStateException("sink '${sink.name}' rejected span '${span.spanId}'")
    }

    override fun flush() = sink.flush()

    private fun redactedAttributes(span: CompletedSpan): Map<String, String> =
        span.attributes.mapValues { (_, value) ->
            val stringified = value?.toString() ?: "null"
            if (value is String) redactor.redact(stringified) else stringified
        }
}
