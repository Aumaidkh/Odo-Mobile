package com.hopcape.performance.api

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// ─────────────────────────────────────────────────────────────
// PerformanceConfig — immutable configuration consumed by
// APM.init(). Mirrors AnalyticsConfig / LoggerConfig: one value
// object, built once at startup, that fully describes the tracer.
// The app supplies device/session context and the sampling knobs;
// the module owns everything else (recording, dispatch, export).
// ─────────────────────────────────────────────────────────────
data class PerformanceConfig(
    /** App version string stamped onto every span's context. */
    val appVersion: String,

    /** Device model (e.g. "Pixel 7") stamped onto every span's context. */
    val deviceModel: String,

    /** OS version string stamped onto every span's context. */
    val osVersion: String,

    /** BCP-47 locale (e.g. "en-IN") stamped onto every span's context. */
    val locale: String,

    /**
     * Debug builds sample every span (nothing dropped) and add a console
     * exporter that prints the span tree. Production builds apply
     * [sampleRate] to ordinary spans — but errors and slow spans are always
     * kept regardless (see the adaptive sampler).
     */
    val isDebug: Boolean = false,

    /**
     * Fraction (0.0..1.0) of *ordinary* spans kept in production. Errored spans
     * and spans slower than [slowSpanThreshold] bypass this and are always kept,
     * so the interesting tail is never sampled away.
     */
    val sampleRate: Double = DEFAULT_SAMPLE_RATE,

    /** Spans at least this slow are always kept, regardless of [sampleRate]. */
    val slowSpanThreshold: Duration = DEFAULT_SLOW_SPAN_THRESHOLD,

    /** Deliver a batch once this many spans are queued (size trigger). */
    val batchSize: Int = DEFAULT_BATCH_SIZE,

    /** Deliver whatever is queued at least this often (time trigger). */
    val flushInterval: Duration = DEFAULT_FLUSH_INTERVAL,

    /**
     * Extra vendor backends (e.g. Firebase Performance) a platform module registers
     * without this module depending on that vendor's SDK. Each is wrapped in the same
     * crash-isolating decorator as the built-in exporters, so a misbehaving sink can
     * never take down delivery to the others.
     */
    val destinations: List<SpanSink> = emptyList(),

    /**
     * Diagnostics channel for the module's own lifecycle (duplicate init, dropped
     * spans, exporter failures). Defaults to a no-op; wire it to your logger in
     * debug builds. Kept as a `String` sink so no internal type leaks across the
     * public boundary.
     */
    val onDiagnostic: (String) -> Unit = {},
) {
    companion object {
        const val DEFAULT_SAMPLE_RATE: Double = 0.2
        const val DEFAULT_BATCH_SIZE: Int = 20
        val DEFAULT_SLOW_SPAN_THRESHOLD: Duration = 2.seconds
        val DEFAULT_FLUSH_INTERVAL: Duration = 15.seconds
    }
}
