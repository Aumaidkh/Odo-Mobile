package com.hopcape.performance.api

// ─────────────────────────────────────────────────────────────
// Span — the minimal public handle to an in-flight timed operation.
// Callers only ever see this interface: they start a span (via APM /
// PerformanceTracer), decorate it with attributes, and end it. They
// never touch the recording buffer, the sampler, or the exporter —
// all of that is `internal` to the module (ISP + information hiding).
//
// A span models a single operation with a meaningful *duration*
// (network call, DB query, render, cold start). Instant events (a
// button tap) should NOT be spans — log/track those instead, or the
// APM data drowns in zero-duration noise.
// ─────────────────────────────────────────────────────────────
interface Span {

    /** Unique id of this span — pass it as `parentSpanId` to nest a child span under it. */
    val spanId: String

    /** The trace this span belongs to. All spans sharing a [traceId] render as one tree. */
    val traceId: String

    /** The enclosing span's [spanId], or null for a root span. */
    val parentSpanId: String?

    /** Stable operation name (e.g. `login_api_call`) — the unit the dashboard aggregates on. */
    val name: String

    /**
     * Attaches a key/value attribute to this span and returns the same span so
     * calls chain (`startSpan(...).setAttribute("launch_type", "cold")`).
     *
     * Setting an attribute named `error` marks the span as failed — the adaptive
     * sampler then captures it unconditionally, so failures are never sampled out.
     */
    fun setAttribute(key: String, value: Any?): Span
}
