package com.hopcape.odo.core.triptracker.engine

import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.odo.core.triptracker.model.SessionSnapshot
import kotlin.time.Duration

/** The two durations the machine cannot detect on its own — a real, engine-scheduled timer. */
internal enum class TimerKind { IDLE, STITCH }

/** What the machine wants reported. Mapped onto real [com.hopcape.odo.core.triptracker.observability.TripTrackerTelemetry] calls by the engine (S5/S9). */
internal sealed interface TripTelemetryEvent {
    data class Started(val mode: TripMode) : TripTelemetryEvent
    data object StitchResumed : TripTelemetryEvent
}

/** Something the engine must do — the machine only ever decides, never acts (§4.1). */
internal sealed interface TripEffect {
    data object StartForegroundSession : TripEffect
    data object StopForegroundSession : TripEffect
    data object RequestFixes : TripEffect
    data object StopFixes : TripEffect
    data class PersistSession(val snapshot: SessionSnapshot) : TripEffect
    data object ClearSession : TripEffect
    data class StartTimer(val kind: TimerKind, val duration: Duration) : TripEffect
    data class CancelTimer(val kind: TimerKind) : TripEffect
    data class Finalize(val session: TripSession) : TripEffect
    data class Telemetry(val event: TripTelemetryEvent) : TripEffect
}

internal data class Transition(val newState: TripPhase, val effects: List<TripEffect>)
