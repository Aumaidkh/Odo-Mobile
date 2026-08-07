package com.hopcape.odo.core.triptracker.observability

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.performance.api.PerformanceTracer

/**
 * Every event name and log line for this module lives here — never scattered through the
 * engine or the finalizer. Never a coordinate, never a Bluetooth MAC (D4) — no method
 * signature below accepts one, so the rule can't be broken at a call site.
 *
 * S9 gives this its final pass (spans, non-fatals, the analytics schema registration);
 * these are the events the engine (S5) already has enough information to report.
 */
class TripTrackerTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    private val tracer: PerformanceTracer,
    private val crash: CrashRecorder,
) {
    fun enabled() = analytics.track(EVENT_TRACKING_ENABLED)

    fun disabled() = analytics.track(EVENT_TRACKING_DISABLED)

    fun started(mode: TripMode) = analytics.track(EVENT_TRIP_STARTED, mapOf(Key.MODE to mode.name))

    fun stitchResumed() = analytics.track(EVENT_TRIP_STITCH_RESUMED)

    fun gapInferred() = analytics.track(EVENT_TRIP_GAP_INFERRED)

    /** [needsConfirmation] mirrors [com.hopcape.odo.core.domain.trip.model.TripStatus.NEEDS_CONFIRMATION]. */
    fun saved(mode: TripMode, needsConfirmation: Boolean, distanceMeters: Long, estimatedMeters: Long) {
        val fields = mapOf(
            Key.MODE to mode.name,
            Key.DISTANCE_KM_BUCKET to distanceKmBucket(distanceMeters),
            Key.ESTIMATED_PCT_BUCKET to estimatedPctBucket(distanceMeters, estimatedMeters),
        )
        analytics.track(if (needsConfirmation) EVENT_TRIP_NEEDS_CONFIRMATION else EVENT_TRIP_SAVED, fields)
    }

    fun discarded(mode: TripMode, distanceMeters: Long) {
        logger.info(TAG, EVENT_TRIP_DISCARDED, fields = mapOf(Key.MODE to mode.name, Key.DISTANCE_M to distanceMeters))
        analytics.track(EVENT_TRIP_DISCARDED, mapOf(Key.MODE to mode.name))
    }

    fun preconditionLost(which: String) = analytics.track(EVENT_PRECONDITION_LOST, mapOf(Key.WHICH to which))

    fun sessionRestored(outcome: String) = analytics.track(EVENT_SESSION_RESTORED, mapOf(Key.OUTCOME to outcome))

    /** A caught exception that means something in the tracking pipeline is broken. */
    fun nonFatal(throwable: Throwable, stage: String) {
        logger.error(TAG, "trip_tracker_error", fields = mapOf(Key.STAGE to stage))
        crash.recordNonFatal(throwable, mapOf(Key.STAGE to stage))
    }

    private fun distanceKmBucket(meters: Long): String = when {
        meters < 1_000 -> "<1"
        meters < 5_000 -> "1-5"
        meters < 20_000 -> "5-20"
        else -> "20+"
    }

    private fun estimatedPctBucket(distanceMeters: Long, estimatedMeters: Long): String {
        if (distanceMeters <= 0L) return "0"
        return when (val pct = estimatedMeters * 100 / distanceMeters) {
            0L -> "0"
            in 1..19 -> "<20"
            in 20..39 -> "20-40"
            else -> "40+"
        }
    }

    /** Field keys — kept here so a dashboard query never breaks on a renamed literal. */
    object Key {
        const val MODE = "mode"
        const val DISTANCE_KM_BUCKET = "distance_km_bucket"
        const val DISTANCE_M = "distance_m"
        const val ESTIMATED_PCT_BUCKET = "estimated_pct_bucket"
        const val WHICH = "which"
        const val OUTCOME = "outcome"
        const val STAGE = "stage"
    }

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
