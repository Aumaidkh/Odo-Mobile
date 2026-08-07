package com.hopcape.odo.core.triptracker.engine

import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.odo.core.triptracker.model.SessionSnapshot
import kotlin.time.Instant

/**
 * Converts between the phases that carry a [TripSession] and the flat [SessionSnapshot]
 * the crash-resume journal stores. [TripSession.lastMotionKind] and
 * [TripSession.attributionConfident] are not persisted — a resumed session assumes
 * "not confident" (the conservative default the validation ladder already downgrades
 * to [com.hopcape.odo.core.domain.trip.model.TripStatus.NEEDS_CONFIRMATION] on) rather
 * than guessing signals the process no longer has.
 */
internal object SessionSnapshotMapper {

    private const val PHASE_TRACKING = "TRACKING"
    private const val PHASE_SOFT_PAUSED = "SOFT_PAUSED"
    private const val PHASE_SIGNAL_LOST = "SIGNAL_LOST"
    private const val PHASE_PENDING_STOP = "PENDING_STOP"

    fun toSnapshot(phase: TripPhase): SessionSnapshot? = when (phase) {
        is TripPhase.Tracking -> snapshot(PHASE_TRACKING, phase.session, stitchDeadline = null)
        is TripPhase.SoftPaused -> snapshot(PHASE_SOFT_PAUSED, phase.session, stitchDeadline = null)
        is TripPhase.SignalLost -> snapshot(PHASE_SIGNAL_LOST, phase.session, stitchDeadline = null)
        is TripPhase.PendingStop -> snapshot(PHASE_PENDING_STOP, phase.session, stitchDeadline = phase.stopDeadline)
        else -> null
    }

    fun fromSnapshot(snapshot: SessionSnapshot): TripPhase? {
        val startedAt = snapshot.startedAt ?: return null
        val mode = snapshot.mode?.let { runCatching { TripMode.valueOf(it) }.getOrNull() } ?: return null
        val session = TripSession(
            startedAt = startedAt,
            mode = mode,
            distanceMeters = snapshot.distanceMeters,
            estimatedMeters = snapshot.estimatedMeters,
            lastGoodFix = snapshot.lastFix,
            attributionConfident = false,
        )
        return when (snapshot.phase) {
            PHASE_TRACKING -> TripPhase.Tracking(session)
            PHASE_SOFT_PAUSED -> TripPhase.SoftPaused(session)
            PHASE_SIGNAL_LOST -> TripPhase.SignalLost(session)
            PHASE_PENDING_STOP -> snapshot.stitchDeadline?.let { TripPhase.PendingStop(session, it) }
            else -> null
        }
    }

    private fun snapshot(phase: String, session: TripSession, stitchDeadline: Instant?) = SessionSnapshot(
        phase = phase,
        carId = null,
        mode = session.mode.name,
        startedAt = session.startedAt,
        distanceMeters = session.distanceMeters,
        estimatedMeters = session.estimatedMeters,
        lastFix = session.lastGoodFix,
        stitchDeadline = stitchDeadline,
    )
}
