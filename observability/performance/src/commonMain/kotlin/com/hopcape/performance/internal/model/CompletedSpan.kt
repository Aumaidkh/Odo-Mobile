package com.hopcape.performance.internal.model

// ─────────────────────────────────────────────────────────────
// CompletedSpan — immutable, fully-resolved span ready for the
// sampler and exporters. Produced at endSpan() from a RecordingSpan.
// `spanId` is the delivery/dead-letter key inside the store &
// dispatcher; `sequenceNumber` preserves ordering. Internal: it
// never crosses the public boundary — callers only ever see [Span].
//
// A span "errored" iff its attributes carry an `error` key; the
// adaptive sampler keys off that to guarantee capture.
// ─────────────────────────────────────────────────────────────
internal data class CompletedSpan(
    val name: String,
    val traceId: String,
    val spanId: String,
    val parentSpanId: String?,
    val attributes: Map<String, Any?>,
    val startEpochMs: Long,
    val durationMs: Long,
    val context: SpanContext,
    val sequenceNumber: Long = 0L,
) {
    /** True when a call site set an `error` attribute — the sampler always keeps these. */
    val isError: Boolean get() = attributes.containsKey(ERROR_ATTRIBUTE)

    companion object {
        const val ERROR_ATTRIBUTE = "error"
    }
}
