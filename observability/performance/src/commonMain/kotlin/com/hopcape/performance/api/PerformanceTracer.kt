package com.hopcape.performance.api

// ─────────────────────────────────────────────────────────────
// PerformanceTracer — the minimal public contract (ISP). This is
// what ViewModels / repositories / use cases depend on and inject.
// They never see the recording buffer, the adaptive sampler, the
// dispatch queue, or the exporters — all of that is `internal`.
//
// Mirrors AnalyticsTracker / Logger in the sibling observability
// modules so the three systems feel like one API family.
// ─────────────────────────────────────────────────────────────
interface PerformanceTracer {

    /**
     * Opens a new span named [name] on trace [traceId]. Pass [parentSpanId] to
     * nest it under a parent span *within the same trace* (e.g. a network call
     * inside a login flow). The returned [Span] is decorated with attributes and
     * later closed via [endSpan]; nothing is recorded until it ends.
     */
    fun startSpan(name: String, traceId: String, parentSpanId: String? = null): Span

    /**
     * Closes [span], computes its duration, applies the sampling decision, and —
     * if kept — enqueues it for export. Ending a span twice is a no-op; ending a
     * foreign/no-op span is ignored (fail-safe).
     */
    fun endSpan(span: Span)

    /** Forces the export queue to attempt delivery now, rather than on the next interval. */
    fun flush()
}
