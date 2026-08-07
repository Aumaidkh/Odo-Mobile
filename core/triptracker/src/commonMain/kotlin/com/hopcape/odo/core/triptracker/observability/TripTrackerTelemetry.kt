package com.hopcape.odo.core.triptracker.observability

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.Logger
import com.hopcape.performance.api.PerformanceTracer

/**
 * Every event name and log line for this module lives here — never scattered through the
 * engine or the finalizer.
 *
 * Skeleton for now: the constructor and the event vocabulary exist so the type is stable
 * for Koin wiring from S2 onward; the methods that actually call [logger]/[analytics]/
 * [tracer]/[crash] land with the engine (S5) and get their final pass in S9.
 */
class TripTrackerTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    private val tracer: PerformanceTracer,
    private val crash: CrashRecorder,
) {
    companion object {
        const val TAG = "triptracker"

        /* Event names. Once shipped these are what the dashboard queries — do not rename. */
        const val EVENT_TRACKING_ENABLED = "trip_tracking_enabled"
        const val EVENT_TRACKING_DISABLED = "trip_tracking_disabled"
        const val EVENT_TRIP_STARTED = "trip_started"
        const val EVENT_TRIP_SAVED = "trip_saved"
        const val EVENT_TRIP_NEEDS_CONFIRMATION = "trip_needs_confirmation"
        const val EVENT_TRIP_DISCARDED = "trip_discarded"
        const val EVENT_TRIP_GAP_INFERRED = "trip_gap_inferred"
        const val EVENT_TRIP_STITCH_RESUMED = "trip_stitch_resumed"
        const val EVENT_PRECONDITION_LOST = "tracking_precondition_lost"
        const val EVENT_SESSION_RESTORED = "trip_session_restored"
    }
}
