package com.hopcape.performance.api

// ─────────────────────────────────────────────────────────────
// SpanSink — the port an outside module implements to add a vendor
// backend (e.g. Firebase Performance) without this module depending
// on that vendor's SDK. Public and deliberately smaller than the
// internal SpanExporter: a sink never sees the internal CompletedSpan
// type, only the resolved values it needs to forward. The analog of
// the analytics module's AnalyticsSink.
//
// Attributes are `Map<String, String>`, not `Map<String, Any?>` — the
// stringify/truncate decision happens once, in the internal adapter
// that calls this port, so every sink receives already-normalized
// values instead of repeating that work.
//
// A sink registered via PerformanceConfig.destinations is wrapped in
// SafeSpanExporter like every built-in exporter, so a throwing sink
// can't crash the host or block delivery to the others.
// ─────────────────────────────────────────────────────────────
interface SpanSink {
    val name: String

    /**
     * Delivers one already-finished span. Returns whether the vendor accepted it —
     * decides whether the dispatcher retries. Return `true` (handled) both when
     * delivery succeeded AND when it failed for a reason retrying can never fix
     * (the vendor SDK is permanently unavailable, the span was rejected as
     * malformed) — only a genuine transient failure should return `false`.
     */
    fun export(
        name: String,
        traceId: String,
        spanId: String,
        parentSpanId: String?,
        startEpochMs: Long,
        durationMs: Long,
        isError: Boolean,
        attributes: Map<String, String>,
    ): Boolean

    fun flush()
}
