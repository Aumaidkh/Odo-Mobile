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
            carRepository.observePrimaryCar().collect { car ->
                primaryCar = car
                parked = car?.let { tripRepository.parkedLocation(it.id) }
            }
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        if (enabled) {
            motionJob = scope.launch {
                motionSource.signals().collect { signal ->
                    motionDebouncer.accept(signal)?.let { handle(TripEvent.Motion(it)) }
                }
            }
            presenceJob = scope.launch {
                presenceSource.presence().collect { presence ->
                    handle(if (presence is VehiclePresence.Connected) TripEvent.PresenceConnected else TripEvent.PresenceLost)
                }
            }
            handle(TripEvent.Enabled)
            sessionStore.load()?.let { handle(TripEvent.SessionRestored(it)) }
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
        for (effect in result.effects) executeEffect(effect)
        maybePersist(previous, phase)
        _status.value = phase.toTrackingStatus(preconditions.status())
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
            locationProvider.fixes(FixRequest(FIX_INTERVAL)).collect { sample ->
                val integration = distanceIntegrator.accept(sample)
                handle(TripEvent.Fix(sample, integration))
            }
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
    }
}
