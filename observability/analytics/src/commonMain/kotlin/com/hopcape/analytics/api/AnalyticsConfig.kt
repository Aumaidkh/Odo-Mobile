package com.hopcape.analytics.api

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// ─────────────────────────────────────────────────────────────
// AnalyticsConfig — immutable configuration consumed by
// HAnalytics.init(). Mirrors LoggerConfig in the logging module:
// one value object, built once at startup, that fully describes
// the pipeline. The app supplies device/session context, its event
// taxonomy, and the batching knobs; the module owns everything else.
// ─────────────────────────────────────────────────────────────
data class AnalyticsConfig(
    /** App version string stamped onto every event's context. */
    val appVersion: String,

    /** Device model (e.g. "Pixel 7") stamped onto every event's context. */
    val deviceModel: String,

    /** OS version string stamped onto every event's context. */
    val osVersion: String,

    /** BCP-47 locale (e.g. "en-IN") stamped onto every event's context. */
    val locale: String,

    /**
     * Debug builds enable strict schema validation (throw on invalid, drop
     * unregistered events) and add a [ConsoleDestination]-style debug output.
     * Production builds are lenient — unknown events pass through so data is
     * never silently lost in the field.
     */
    val isDebug: Boolean = false,

    /**
     * The event taxonomy. Registered events are type-checked against their
     * schema; see [EventSchema]. Empty means "no schema enforcement" — every
     * event is treated as unregistered.
     */
    val events: List<EventSchema> = emptyList(),

    /** Deliver a batch once this many events are queued (size trigger). */
    val batchSize: Int = DEFAULT_BATCH_SIZE,

    /** Deliver whatever is queued at least this often (time trigger). */
    val flushInterval: Duration = DEFAULT_FLUSH_INTERVAL,

    /**
     * Extra vendor destinations contributed by an outside module (e.g. Firebase),
     * alongside the built-in PostHog destination. Each is wrapped in the same
     * crash-isolating decorator as the built-ins, so a misbehaving sink can never
     * take down delivery to the others.
     */
    val destinations: List<AnalyticsSink> = emptyList(),

    /**
     * Diagnostics channel for the module's own lifecycle (duplicate init,
     * dropped events, schema violations, destination failures). Defaults to a
     * no-op; wire it to your logger in debug builds. Kept as a `String` sink so
     * no internal type leaks across the public boundary.
     */
    val onDiagnostic: (String) -> Unit = {},
) {
    companion object {
        const val DEFAULT_BATCH_SIZE: Int = 20
        val DEFAULT_FLUSH_INTERVAL: Duration = 10.seconds
    }
}
