package com.hopcape.odo.core.triptracker.engine

import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.odo.core.triptracker.model.LocationSample
import com.hopcape.odo.core.triptracker.model.MotionKind
import kotlin.time.Instant

/**
 * Everything true about the trip currently in progress — carried unchanged by every
 * "live" phase (TRACKING through FINALIZING) so a phase change never loses it.
 *
 * [attributionConfident] is [com.hopcape.odo.core.triptracker.algorithm.AttributionResult.confident]
 * at start time — carried forward so the finalizer's validation ladder can downgrade a
 * radius-marginal trip regardless of size (§4.4), without re-running the check later.
 *
 * [firstFix] is the trip's opening point — unknown at BT_VERIFIED start (no fix was
 * involved in that decision), so it is captured from the first [TripEvent.Fix] TRACKING
 * receives rather than at [TripPhase.Starting]. The finalizer needs it as the trip's
 * start coordinate and to decide whether a BT_VERIFIED trip needs a GAP_INFERRED twin
 * (§4.5). Not persisted in [SessionSnapshot] — lost on a kill before the first post-resume
 * fix, which only costs that one trip's gap-inferred twin, not its correctness.
 */
internal data class TripSession(
    val startedAt: Instant,
    val mode: TripMode,
    val distanceMeters: Long = 0,
    val estimatedMeters: Long = 0,
    val firstFix: LocationSample? = null,
    val lastGoodFix: LocationSample? = null,
    val lastMotionKind: MotionKind? = null,
    val attributionConfident: Boolean,
)

/** The live session carried by this phase, or `null` for phases with none. */
internal fun TripPhase.sessionOrNull(): TripSession? = when (this) {
    is TripPhase.Tracking -> session
    is TripPhase.SoftPaused -> session
    is TripPhase.SignalLost -> session
    is TripPhase.PendingStop -> session
    is TripPhase.Finalizing -> session
    else -> null
}

/**
 * The trip tracker's state, one value per §4.2. Persisted via [SessionSnapshotMapper] so
 * a process kill resumes rather than silently drops the trip in progress.
 */
internal sealed interface TripPhase {

    data object Disabled : TripPhase

    /**
     * Waiting for a trip trigger. [lastMotionKind] and [presenceConnected] are the
     * latest debounced signals, kept so a BT connect and an already-settled IN_VEHICLE
     * reading (or vice versa) don't have to arrive in the same instant to start a trip.
     * [gpsMotionSince] is non-null while a GPS-only candidate is sustaining toward the
     * 45 s threshold (§4.2's GPS path). [speedGateAboveCount] is STEREO's parallel start
     * confirm (auto-odometer plan §1.1/§6): consecutive fixes read above
     * [com.hopcape.odo.core.triptracker.config.TripTrackerConfig.speedGateThresholdKmh]
     * since the bonded stereo connected, reset by any fix at or below it.
     */
    data class Standby(
        val lastMotionKind: MotionKind? = null,
        val presenceConnected: Boolean = false,
        val gpsMotionSince: Instant? = null,
        val speedGateAboveCount: Int = 0,
    ) : TripPhase

    /**
     * Decided to start; [com.hopcape.odo.core.triptracker.engine.TripEffect.StartForegroundSession]
     * has been requested. Resolves to [Tracking] the moment the next event arrives —
     * there is no separate "FGS confirmed" signal to wait for.
     */
    data class Starting(
        val mode: TripMode,
        val since: Instant,
        val attributionConfident: Boolean,
    ) : TripPhase

    /** Enabled and measuring. [stopCandidateSince] tracks a GPS-only stop candidate (§4.2). */
    data class Tracking(
        val session: TripSession,
        val stopCandidateSince: Instant? = null,
    ) : TripPhase

    /** STILL sustained ≥ [com.hopcape.odo.core.triptracker.config.TripTrackerConfig.idleSoftPauseThreshold] — fixes released, session kept. */
    data class SoftPaused(val session: TripSession) : TripPhase

    /** A distance-integration gap is open — waiting for a fix good enough to close it. */
    data class SignalLost(val session: TripSession, val stopCandidateSince: Instant? = null) : TripPhase

    /** The stop trigger fired; [stopDeadline] is when the stitch window (§4.2) closes. */
    data class PendingStop(val session: TripSession, val stopDeadline: Instant) : TripPhase

    /** [com.hopcape.odo.core.triptracker.engine.TripEffect.Finalize] has been requested; resolves to [Standby] on the next event. */
    data class Finalizing(val session: TripSession) : TripPhase
}
