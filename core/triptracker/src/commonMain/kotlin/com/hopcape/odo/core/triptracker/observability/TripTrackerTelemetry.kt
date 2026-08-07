package com.hopcape.odo.core.triptracker.observability

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span

/**
 * Every event name and log line for this module lives here — never scattered through the
 * engine or the finalizer. Never a coordinate, never a Bluetooth MAC (D4) — no method
 * signature below accepts one, so the rule can't be broken at a call site.
 *
 * Deliberately uninstrumented, said out loud rather than left as an oversight: the pure
 * algorithm and state-machine classes, the row mappers, and every value object. A wrong
 * transition shows up in [saved]'s numbers, not in a log line — instrumenting the pure
 * layer would only duplicate what the outcome already reports.
 */
class TripTrackerTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    private val tracer: PerformanceTracer,
    private val crash: CrashRecorder,
) {
    /** The current trip's span, from [started] to whichever of [saved]/[discarded] closes it. */
    private var activeTripSpan: Span? = null

    fun enabled() = analytics.track(EVENT_TRACKING_ENABLED)

    fun disabled() = analytics.track(EVENT_TRACKING_DISABLED)

    fun started(mode: TripMode) {
        // UNTRACED, not the caller's coroutine trace: a trip's span outlives whatever
        // request/effect happened to start it, the same reason it isn't opened inside
        // DataTelemetry.span's per-call scoping.
        activeTripSpan = tracer.startSpan(name = "$TAG.trip", traceId = UNTRACED)
            .setAttribute(Key.MODE, mode.name)
        analytics.track(EVENT_TRIP_STARTED, mapOf(Key.MODE to mode.name))
    }

    fun stitchResumed() = analytics.track(EVENT_TRIP_STITCH_RESUMED)

    fun gapInferred() = analytics.track(EVENT_TRIP_GAP_INFERRED)

    /** [needsConfirmation] mirrors [com.hopcape.odo.core.domain.trip.model.TripStatus.NEEDS_CONFIRMATION]. */
    fun saved(mode: TripMode, needsConfirmation: Boolean, distanceMeters: Long, estimatedMeters: Long) {
        endActiveTripSpan()
        val fields = mapOf(
            Key.MODE to mode.name,
            Key.DISTANCE_KM_BUCKET to distanceKmBucket(distanceMeters),
            Key.ESTIMATED_PCT_BUCKET to estimatedPctBucket(distanceMeters, estimatedMeters),
        )
        analytics.track(if (needsConfirmation) EVENT_TRIP_NEEDS_CONFIRMATION else EVENT_TRIP_SAVED, fields)
    }

    /** [reason] is a fixed label ([REASON_BELOW_FLOOR] / [REASON_INVALID]), never free text. */
    fun discarded(mode: TripMode, distanceMeters: Long, reason: String) {
        endActiveTripSpan()
        val fields = mapOf(Key.MODE to mode.name, Key.DISTANCE_M to distanceMeters, Key.REASON to reason)
        logger.info(TAG, EVENT_TRIP_DISCARDED, fields = fields)
        analytics.track(EVENT_TRIP_DISCARDED, mapOf(Key.MODE to mode.name, Key.REASON to reason))
    }

    fun preconditionLost(which: String) = analytics.track(EVENT_PRECONDITION_LOST, mapOf(Key.WHICH to which))

    fun sessionRestored(outcome: String) = analytics.track(EVENT_SESSION_RESTORED, mapOf(Key.OUTCOME to outcome))

    /** A caught exception that means something in the tracking pipeline is broken. */
    fun nonFatal(throwable: Throwable, stage: String) {
        logger.error(TAG, "trip_tracker_error", fields = mapOf(Key.STAGE to stage))
        crash.recordNonFatal(throwable, mapOf(Key.STAGE to stage))
    }

    private fun endActiveTripSpan() {
        activeTripSpan?.let { tracer.endSpan(it) }
        activeTripSpan = null
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
        const val REASON = "reason"
    }

    companion object {
        const val TAG = "triptracker"
        const val UNTRACED = "untraced"

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

        /* discarded()'s fixed reason labels. */
        const val REASON_BELOW_FLOOR = "below_validation_floor"
        const val REASON_INVALID = "invalid_trip_data"

        /* sessionRestored()'s fixed outcome labels. */
        const val OUTCOME_RESUMED_TRACKING = "resumed_tracking"
        const val OUTCOME_RESUMED_SOFT_PAUSED = "resumed_soft_paused"
        const val OUTCOME_RESUMED_PENDING_STOP = "resumed_pending_stop"
        const val OUTCOME_FINALIZED_EXPIRED = "finalized_expired"
        const val OUTCOME_MALFORMED = "malformed"
    }
}
