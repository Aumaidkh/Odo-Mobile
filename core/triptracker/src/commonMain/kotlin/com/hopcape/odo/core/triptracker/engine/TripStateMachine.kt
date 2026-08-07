package com.hopcape.odo.core.triptracker.engine

import com.hopcape.odo.core.domain.trip.model.GeoPoint
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.odo.core.triptracker.algorithm.TripAttribution
import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import com.hopcape.odo.core.triptracker.model.MotionKind
import kotlin.time.Instant

/**
 * `(state, event) -> (newState, effects)` — every decision in §4.2 lives here: start,
 * debounce-driven stitch/idle/stop, and attribution. Nothing here acts; every effect is
 * a value the engine executes.
 *
 * Takes [parked] alongside the plan's documented `(state, event, config, now)` shape:
 * §4.1 names "attribute" as one of this machine's owned decisions, and attribution
 * needs the car's last parked point ([TripAttribution]) — a value with no other way to
 * reach a pure function. The engine reads it cheaply (one row) before every dispatch;
 * the machine stays pure and fully unit-testable either way.
 */
internal object TripStateMachine {

    fun transition(
        state: TripPhase,
        event: TripEvent,
        config: TripTrackerConfig,
        now: Instant,
        parked: ParkedLocation?,
    ): Transition {
        // Enable/disable/restore are cross-cutting: every phase answers them the same
        // way, so they are resolved before the per-phase handlers see the event.
        when (event) {
            TripEvent.Disabled -> return handleDisabled(state)
            TripEvent.Enabled -> if (state is TripPhase.Disabled) return Transition(TripPhase.Standby(), emptyList())
            is TripEvent.SessionRestored ->
                if (state is TripPhase.Disabled || state is TripPhase.Standby) return handleSessionRestored(event, now)
            else -> Unit
        }

        return when (state) {
            TripPhase.Disabled -> Transition(state, emptyList())
            is TripPhase.Standby -> handleStandby(state, event, config, now, parked)
            is TripPhase.Starting -> handleStarting(state, event, config, now)
            is TripPhase.Tracking -> handleTracking(state, event, config, now)
            is TripPhase.SoftPaused -> handleSoftPaused(state, event)
            is TripPhase.SignalLost -> handleSignalLost(state, event)
            is TripPhase.PendingStop -> handlePendingStop(state, event, config, now)
            is TripPhase.Finalizing -> handleFinalizing()
        }
    }

    // ---- cross-cutting ----

    private fun handleDisabled(state: TripPhase): Transition {
        if (state is TripPhase.Disabled) return Transition(state, emptyList())
        val session = state.sessionOrNull()
        val effects = buildList {
            add(TripEffect.StopForegroundSession)
            add(TripEffect.StopFixes)
            add(TripEffect.CancelTimer(TimerKind.IDLE))
            add(TripEffect.CancelTimer(TimerKind.STITCH))
            if (session != null) add(TripEffect.Finalize(session))
            add(TripEffect.ClearSession)
        }
        return Transition(TripPhase.Disabled, effects)
    }

    /** Only reachable from DISABLED/STANDBY — a restore only matters right at cold start. */
    private fun handleSessionRestored(event: TripEvent.SessionRestored, now: Instant): Transition {
        val restored = SessionSnapshotMapper.fromSnapshot(event.snapshot)
            ?: return Transition(TripPhase.Standby(), emptyList())

        if (restored is TripPhase.PendingStop && now >= restored.stopDeadline) {
            return Transition(TripPhase.Finalizing(restored.session), listOf(TripEffect.Finalize(restored.session)))
        }

        val effects = buildList {
            add(TripEffect.StartForegroundSession)
            if (restored !is TripPhase.SoftPaused) add(TripEffect.RequestFixes)
            if (restored is TripPhase.PendingStop) add(TripEffect.StartTimer(TimerKind.STITCH, restored.stopDeadline - now))
        }
        return Transition(restored, effects)
    }

    private fun TripPhase.sessionOrNull(): TripSession? = when (this) {
        is TripPhase.Tracking -> session
        is TripPhase.SoftPaused -> session
        is TripPhase.SignalLost -> session
        is TripPhase.PendingStop -> session
        is TripPhase.Finalizing -> session
        else -> null
    }

    // ---- STANDBY ----

    private fun handleStandby(
        state: TripPhase.Standby,
        event: TripEvent,
        config: TripTrackerConfig,
        now: Instant,
        parked: ParkedLocation?,
    ): Transition = when (event) {
        TripEvent.PresenceConnected ->
            if (state.lastMotionKind == MotionKind.IN_VEHICLE) {
                startTrip(TripMode.BT_VERIFIED, now, attributionConfident = true)
            } else {
                Transition(state.copy(presenceConnected = true), emptyList())
            }

        TripEvent.PresenceLost -> Transition(state.copy(presenceConnected = false), emptyList())

        is TripEvent.Motion -> when {
            event.kind == MotionKind.IN_VEHICLE && state.presenceConnected ->
                startTrip(TripMode.BT_VERIFIED, now, attributionConfident = true)

            event.kind == MotionKind.IN_VEHICLE && state.gpsMotionSince == null ->
                Transition(state.copy(lastMotionKind = event.kind, gpsMotionSince = now), listOf(TripEffect.RequestFixes))

            event.kind == MotionKind.IN_VEHICLE ->
                Transition(state.copy(lastMotionKind = event.kind), emptyList())

            else -> {
                val effects = if (state.gpsMotionSince != null) listOf(TripEffect.StopFixes) else emptyList()
                Transition(state.copy(lastMotionKind = event.kind, gpsMotionSince = null), effects)
            }
        }

        is TripEvent.Fix -> handleStandbyFix(state, event, config, now, parked)

        else -> Transition(state, emptyList())
    }

    private fun handleStandbyFix(
        state: TripPhase.Standby,
        event: TripEvent.Fix,
        config: TripTrackerConfig,
        now: Instant,
        parked: ParkedLocation?,
    ): Transition {
        val since = state.gpsMotionSince ?: return Transition(state, emptyList())
        if (now - since < config.gpsStartSustainedMotion) return Transition(state, emptyList())

        val point = GeoPoint.of(event.sample.lat, event.sample.lon).getOrNull()
            ?: return Transition(state.copy(gpsMotionSince = null), listOf(TripEffect.StopFixes))

        val result = TripAttribution(config).evaluate(TripMode.GPS_ONLY, point, parked)
        return if (result.allowed) {
            startTrip(TripMode.GPS_ONLY, now, attributionConfident = result.confident)
        } else {
            Transition(state.copy(gpsMotionSince = null), listOf(TripEffect.StopFixes))
        }
    }

    private fun startTrip(mode: TripMode, now: Instant, attributionConfident: Boolean): Transition = Transition(
        TripPhase.Starting(mode, now, attributionConfident),
        listOf(TripEffect.StartForegroundSession, TripEffect.Telemetry(TripTelemetryEvent.Started(mode))),
    )

    // ---- STARTING ----

    /** Resolves to TRACKING immediately, then lets TRACKING handle this same event. */
    private fun handleStarting(state: TripPhase.Starting, event: TripEvent, config: TripTrackerConfig, now: Instant): Transition {
        val session = TripSession(
            startedAt = state.since,
            mode = state.mode,
            attributionConfident = state.attributionConfident,
        )
        return handleTracking(TripPhase.Tracking(session), event, config, now)
    }

    // ---- TRACKING ----

    private fun handleTracking(state: TripPhase.Tracking, event: TripEvent, config: TripTrackerConfig, now: Instant): Transition =
        when (event) {
            TripEvent.PresenceLost -> when {
                state.session.mode != TripMode.BT_VERIFIED -> Transition(state, emptyList())
                state.session.lastMotionKind == MotionKind.IN_VEHICLE ->
                    // BT glitch downgrade (v3 §2-#9): still moving, so the trip continues —
                    // just no longer on bond evidence.
                    Transition(state.copy(session = state.session.copy(mode = TripMode.GPS_ONLY)), emptyList())
                else -> enterPendingStop(state.session, config, now)
            }

            TripEvent.IdleTimeout -> Transition(TripPhase.SoftPaused(state.session), listOf(TripEffect.StopFixes))

            is TripEvent.Motion -> handleTrackingMotion(state, event, config, now)

            is TripEvent.Fix -> handleTrackingFix(state, event, config, now)

            else -> Transition(state, emptyList())
        }

    private fun handleTrackingMotion(
        state: TripPhase.Tracking,
        event: TripEvent.Motion,
        config: TripTrackerConfig,
        now: Instant,
    ): Transition {
        val session = state.session.copy(lastMotionKind = event.kind)

        if (event.kind == MotionKind.IN_VEHICLE) {
            return Transition(
                state.copy(session = session, stopCandidateSince = null),
                listOf(TripEffect.CancelTimer(TimerKind.IDLE)),
            )
        }

        val stopCandidateSince = state.stopCandidateSince ?: now
        val effects = if (event.kind == MotionKind.STILL) {
            listOf(TripEffect.StartTimer(TimerKind.IDLE, config.idleSoftPauseThreshold))
        } else {
            emptyList()
        }
        return Transition(state.copy(session = session, stopCandidateSince = stopCandidateSince), effects)
    }

    private fun handleTrackingFix(state: TripPhase.Tracking, event: TripEvent.Fix, config: TripTrackerConfig, now: Instant): Transition {
        val session = state.session.copy(
            distanceMeters = state.session.distanceMeters + event.integration.addedMeters,
            lastGoodFix = event.sample,
        )
        if (event.integration.gapOpened) {
            return Transition(TripPhase.SignalLost(session, state.stopCandidateSince), emptyList())
        }

        val since = state.stopCandidateSince
        val stopMatured = session.mode == TripMode.GPS_ONLY &&
            since != null &&
            session.lastMotionKind != null &&
            session.lastMotionKind != MotionKind.IN_VEHICLE &&
            now - since >= config.gpsStopStillOrWalking

        return if (stopMatured) {
            enterPendingStop(session, config, now)
        } else {
            Transition(state.copy(session = session), emptyList())
        }
    }

    private fun enterPendingStop(session: TripSession, config: TripTrackerConfig, now: Instant): Transition {
        val deadline = now + config.stitchWindow
        return Transition(
            TripPhase.PendingStop(session, deadline),
            listOf(TripEffect.CancelTimer(TimerKind.IDLE), TripEffect.StartTimer(TimerKind.STITCH, config.stitchWindow)),
        )
    }

    // ---- SOFT_PAUSED ----

    private fun handleSoftPaused(state: TripPhase.SoftPaused, event: TripEvent): Transition = when (event) {
        TripEvent.PresenceConnected -> Transition(TripPhase.Tracking(state.session), listOf(TripEffect.RequestFixes))
        is TripEvent.Motion -> Transition(
            TripPhase.Tracking(state.session.copy(lastMotionKind = event.kind)),
            listOf(TripEffect.RequestFixes),
        )
        else -> Transition(state, emptyList())
    }

    // ---- SIGNAL_LOST ----

    private fun handleSignalLost(state: TripPhase.SignalLost, event: TripEvent): Transition = when (event) {
        is TripEvent.Fix -> {
            val session = state.session.copy(
                distanceMeters = state.session.distanceMeters + event.integration.addedMeters,
                lastGoodFix = event.sample,
            )
            if (event.integration.gapClosed != null) {
                Transition(TripPhase.Tracking(session, state.stopCandidateSince), emptyList())
            } else {
                Transition(state.copy(session = session), emptyList())
            }
        }
        is TripEvent.Motion -> Transition(state.copy(session = state.session.copy(lastMotionKind = event.kind)), emptyList())
        else -> Transition(state, emptyList())
    }

    // ---- PENDING_STOP ----

    private fun handlePendingStop(state: TripPhase.PendingStop, event: TripEvent, config: TripTrackerConfig, now: Instant): Transition =
        when (event) {
            TripEvent.StitchWindowExpired -> Transition(
                TripPhase.Finalizing(state.session),
                listOf(TripEffect.StopForegroundSession, TripEffect.StopFixes, TripEffect.Finalize(state.session)),
            )
            TripEvent.PresenceConnected -> resumeFromStitch(state)
            is TripEvent.Motion -> if (event.kind == MotionKind.IN_VEHICLE) resumeFromStitch(state) else Transition(state, emptyList())
            is TripEvent.Fix -> {
                val session = state.session.copy(
                    distanceMeters = state.session.distanceMeters + event.integration.addedMeters,
                    lastGoodFix = event.sample,
                )
                Transition(state.copy(session = session), emptyList())
            }
            else -> Transition(state, emptyList())
        }

    private fun resumeFromStitch(state: TripPhase.PendingStop): Transition = Transition(
        TripPhase.Tracking(state.session),
        listOf(TripEffect.CancelTimer(TimerKind.STITCH), TripEffect.Telemetry(TripTelemetryEvent.StitchResumed)),
    )

    // ---- FINALIZING ----

    /** Resolves to STANDBY on the very next event, regardless of what it is. */
    private fun handleFinalizing(): Transition = Transition(TripPhase.Standby(), listOf(TripEffect.ClearSession))
}
