package com.hopcape.odo.core.triptracker.engine

import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import com.hopcape.odo.core.triptracker.TrackingPreconditions
import com.hopcape.odo.core.triptracker.TrackingReadiness
import com.hopcape.odo.core.triptracker.TrackingStatus
import com.hopcape.odo.core.triptracker.algorithm.DistanceIntegrator
import com.hopcape.odo.core.triptracker.algorithm.MotionDebouncer
import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import com.hopcape.odo.core.triptracker.model.FixRequest
import com.hopcape.odo.core.triptracker.model.VehiclePresence
import com.hopcape.odo.core.triptracker.observability.TripTrackerTelemetry
import com.hopcape.odo.core.triptracker.port.LocationProvider
import com.hopcape.odo.core.triptracker.port.MotionActivitySource
import com.hopcape.odo.core.triptracker.port.TripForegroundSession
import com.hopcape.odo.core.triptracker.port.TripSessionStore
import com.hopcape.odo.core.triptracker.port.VehiclePresenceSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Subscribes to every signal port, feeds [TripEvent]s to [TripStateMachine], and executes
 * the effects it returns. Owns the one [DistanceIntegrator] and [MotionDebouncer] instance
 * — both stateful, so the pure machine cannot hold them itself (§4.1).
 *
 * [now] is injected so tests can drive the machine's `now` in lockstep with `runTest`'s
 * virtual clock, the same reason [scope] is injected rather than captured internally.
 */
internal class TripTrackerEngine(
    private val locationProvider: LocationProvider,
    private val motionSource: MotionActivitySource,
    private val presenceSource: VehiclePresenceSource,
    private val foregroundSession: TripForegroundSession,
    private val sessionStore: TripSessionStore,
    private val preconditions: TrackingPreconditions,
    private val carRepository: CarRepository,
    private val tripRepository: TripRepository,
    private val finalizer: TripFinalizer,
    private val telemetry: TripTrackerTelemetry,
    private val config: TripTrackerConfig,
    private val scope: CoroutineScope,
    private val now: () -> Instant,
) {
    private val distanceIntegrator = DistanceIntegrator(config)
    private val motionDebouncer = MotionDebouncer(config)
    private val mutex = Mutex()

    private var phase: TripPhase = TripPhase.Disabled
    private var parked: ParkedLocation? = null
    private var primaryCar: Car? = null
    private var lastPersistedDistance: Long = 0
    private var lastReadiness: TrackingReadiness? = null

    private var carJob: Job? = null
    private var motionJob: Job? = null
    private var presenceJob: Job? = null
    private var fixesJob: Job? = null
    private var idleTimerJob: Job? = null
    private var stitchTimerJob: Job? = null

    private val _status = MutableStateFlow<TrackingStatus>(TrackingStatus.Disabled)
    val status: StateFlow<TrackingStatus> = _status.asStateFlow()

    /** Started once, regardless of the enabled flag — cheap, and attribution needs it live. */
    fun observeCar() {
        if (carJob?.isActive == true) return
        carJob = scope.launch {
            safely(STAGE_CAR_COLLECT) {
                carRepository.observePrimaryCar().collect { car ->
                    primaryCar = car
                    parked = car?.let { tripRepository.parkedLocation(it.id) }
                }
            }
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        if (enabled) {
            motionJob = scope.launch {
                safely(STAGE_MOTION_COLLECT) {
                    motionSource.signals().collect { signal ->
                        motionDebouncer.accept(signal)?.let { handle(TripEvent.Motion(it)) }
                    }
                }
            }
            presenceJob = scope.launch {
                safely(STAGE_PRESENCE_COLLECT) {
                    presenceSource.presence().collect { presence ->
                        handle(if (presence is VehiclePresence.Connected) TripEvent.PresenceConnected else TripEvent.PresenceLost)
                    }
                }
            }
            handle(TripEvent.Enabled)
            val restored = try {
                sessionStore.load()
            } catch (e: Exception) {
                telemetry.nonFatal(e, stage = STAGE_SESSION_LOAD)
                null
            }
            restored?.let {
                handle(TripEvent.SessionRestored(it))
                reportSessionRestoreOutcome()
            }
        } else {
            handle(TripEvent.Disabled)
            motionJob?.cancel()
            presenceJob?.cancel()
            fixesJob?.cancel()
            idleTimerJob?.cancel()
            stitchTimerJob?.cancel()
        }
    }

    private suspend fun handle(event: TripEvent): Unit = mutex.withLock {
        val result = TripStateMachine.transition(phase, event, config, now(), parked)
        val previous = phase
        phase = result.newState
        // The state transition itself is pure and can't fail; an effect touching real IO
        // (session store, foreground service) can. One effect's failure must not stop the
        // rest, and must not kill the collector coroutine that got us here — the next
        // signal from the same source would silently never arrive again.
        for (effect in result.effects) {
            try {
                executeEffect(effect)
            } catch (e: Exception) {
                telemetry.nonFatal(e, stage = "effect.${effect::class.simpleName}")
            }
        }
        try {
            maybePersist(previous, phase)
        } catch (e: Exception) {
            telemetry.nonFatal(e, stage = STAGE_SESSION_PERSIST)
        }
        val readiness = preconditions.status()
        reportPreconditionLost(readiness)
        _status.value = phase.toTrackingStatus(readiness)
    }

    /** Fires only on a true->false transition — nothing to report the first time readiness is read. */
    private fun reportPreconditionLost(current: TrackingReadiness) {
        val previous = lastReadiness
        lastReadiness = current
        if (previous == null) return
        if (previous.fineLocation && !current.fineLocation) telemetry.preconditionLost("fine_location")
        if (previous.backgroundLocation && !current.backgroundLocation) telemetry.preconditionLost("background_location")
        if (previous.activityRecognition && !current.activityRecognition) telemetry.preconditionLost("activity_recognition")
        if (previous.bluetoothConnect && !current.bluetoothConnect) telemetry.preconditionLost("bluetooth_connect")
        if (previous.notifications && !current.notifications) telemetry.preconditionLost("notifications")
    }

    /** Called once, right after a [TripEvent.SessionRestored] has been processed. */
    private fun reportSessionRestoreOutcome() {
        val outcome = when (phase) {
            is TripPhase.Tracking -> TripTrackerTelemetry.OUTCOME_RESUMED_TRACKING
            is TripPhase.SoftPaused -> TripTrackerTelemetry.OUTCOME_RESUMED_SOFT_PAUSED
            is TripPhase.PendingStop -> TripTrackerTelemetry.OUTCOME_RESUMED_PENDING_STOP
            is TripPhase.Finalizing -> TripTrackerTelemetry.OUTCOME_FINALIZED_EXPIRED
            else -> {
                // A snapshot existed but SessionSnapshotMapper couldn't turn it into a live
                // phase — landed back in Standby, same as no snapshot, but this case is
                // actually unexpected and worth a trace to find out why.
                telemetry.nonFatal(IllegalStateException("session snapshot did not parse into a live phase"), stage = STAGE_SESSION_LOAD)
                TripTrackerTelemetry.OUTCOME_MALFORMED
            }
        }
        telemetry.sessionRestored(outcome)
    }

    private suspend fun executeEffect(effect: TripEffect) {
        when (effect) {
            TripEffect.StartForegroundSession -> foregroundSession.start()
            TripEffect.StopForegroundSession -> foregroundSession.stop()
            TripEffect.RequestFixes -> startFixes()
            TripEffect.StopFixes -> stopFixes()
            TripEffect.ClearSession -> {
                sessionStore.clear()
                lastPersistedDistance = 0
            }
            is TripEffect.StartTimer -> startTimer(effect.kind, effect.duration)
            is TripEffect.CancelTimer -> cancelTimer(effect.kind)
            is TripEffect.Finalize -> finalizeSession(effect.session)
            is TripEffect.Telemetry -> reportTelemetry(effect.event)
        }
    }

    private fun startFixes() {
        fixesJob?.cancel()
        fixesJob = scope.launch {
            safely(STAGE_FIXES_COLLECT) {
                locationProvider.fixes(FixRequest(FIX_INTERVAL)).collect { sample ->
                    val integration = distanceIntegrator.accept(sample)
                    handle(TripEvent.Fix(sample, integration))
                }
            }
        }
    }

    /**
     * A signal-source collector dying silently is worse than it never having started —
     * the next event from that source (a permission revoked mid-trip, a Play Services
     * hiccup) would simply never arrive again with nothing to explain why.
     */
    private suspend fun safely(stage: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            telemetry.nonFatal(e, stage = stage)
        }
    }

    private fun stopFixes() {
        fixesJob?.cancel()
        fixesJob = null
    }

    private fun startTimer(kind: TimerKind, duration: Duration) {
        val job = scope.launch {
            delay(duration)
            handle(if (kind == TimerKind.IDLE) TripEvent.IdleTimeout else TripEvent.StitchWindowExpired)
        }
        when (kind) {
            TimerKind.IDLE -> {
                idleTimerJob?.cancel()
                idleTimerJob = job
            }
            TimerKind.STITCH -> {
                stitchTimerJob?.cancel()
                stitchTimerJob = job
            }
        }
    }

    private fun cancelTimer(kind: TimerKind) {
        when (kind) {
            TimerKind.IDLE -> {
                idleTimerJob?.cancel()
                idleTimerJob = null
            }
            TimerKind.STITCH -> {
                stitchTimerJob?.cancel()
                stitchTimerJob = null
            }
        }
    }

    private suspend fun finalizeSession(session: TripSession) {
        val car = primaryCar ?: run {
            // Shouldn't happen — tracking requires a car to enable — but a silent return
            // here would drop the trip the owner just drove with no trace of why.
            telemetry.nonFatal(IllegalStateException("no primary car at finalize time"), stage = STAGE_FINALIZE_NO_CAR)
            return
        }
        finalizer.finalize(session, endedAt = now(), carId = car.id, ownerId = car.ownerId, parked = parked)
    }

    private fun reportTelemetry(event: TripTelemetryEvent) {
        when (event) {
            is TripTelemetryEvent.Started -> telemetry.started(event.mode)
            TripTelemetryEvent.StitchResumed -> telemetry.stitchResumed()
        }
    }

    /** On every state change, and every ~250 m of distance since the last write (§5.2). */
    private suspend fun maybePersist(previous: TripPhase, current: TripPhase) {
        val snapshot = SessionSnapshotMapper.toSnapshot(current) ?: return
        val session = current.sessionOrNull() ?: return
        val changedPhaseType = previous::class != current::class
        val crossedMilestone = session.distanceMeters - lastPersistedDistance >= PERSIST_DISTANCE_MILESTONE_M
        if (changedPhaseType || crossedMilestone) {
            sessionStore.save(snapshot)
            lastPersistedDistance = session.distanceMeters
        }
    }

    private fun TripPhase.toTrackingStatus(readiness: TrackingReadiness): TrackingStatus = when (this) {
        TripPhase.Disabled -> TrackingStatus.Disabled
        is TripPhase.Standby -> TrackingStatus.Standby(readiness)
        is TripPhase.Starting -> TrackingStatus.Tracking(since, mode)
        is TripPhase.Tracking -> TrackingStatus.Tracking(session.startedAt, session.mode)
        is TripPhase.SoftPaused -> TrackingStatus.Tracking(session.startedAt, session.mode)
        is TripPhase.SignalLost -> TrackingStatus.Tracking(session.startedAt, session.mode)
        is TripPhase.PendingStop -> TrackingStatus.Tracking(session.startedAt, session.mode)
        is TripPhase.Finalizing -> TrackingStatus.Tracking(session.startedAt, session.mode)
    }

    private companion object {
        val FIX_INTERVAL = 1.seconds
        const val PERSIST_DISTANCE_MILESTONE_M = 250L
        const val STAGE_FINALIZE_NO_CAR = "finalize_no_car"
        const val STAGE_SESSION_LOAD = "session_load"
        const val STAGE_SESSION_PERSIST = "session_persist"
        const val STAGE_MOTION_COLLECT = "motion_collect"
        const val STAGE_PRESENCE_COLLECT = "presence_collect"
        const val STAGE_FIXES_COLLECT = "fixes_collect"
        const val STAGE_CAR_COLLECT = "car_collect"
    }
}
