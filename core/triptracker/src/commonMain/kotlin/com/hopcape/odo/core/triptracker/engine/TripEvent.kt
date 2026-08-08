package com.hopcape.odo.core.triptracker.engine

import com.hopcape.odo.core.triptracker.algorithm.IntegrationResult
import com.hopcape.odo.core.triptracker.model.LocationSample
import com.hopcape.odo.core.triptracker.model.MotionKind
import com.hopcape.odo.core.triptracker.model.SessionSnapshot

internal sealed interface TripEvent {

    /** Only ever sent for the bonded device — the engine checks [com.hopcape.odo.core.triptracker.VehicleBondStore] first. */
    data object PresenceConnected : TripEvent

    data object PresenceLost : TripEvent

    /** Only sent on a debounce settle — every kind here is already trusted (§4.2). */
    data class Motion(val kind: MotionKind) : TripEvent

    /**
     * [integration] is the engine's own [com.hopcape.odo.core.triptracker.algorithm.DistanceIntegrator]
     * result for [sample] — the state machine never measures distance itself, only
     * accumulates the delta it is handed.
     */
    data class Fix(val sample: LocationSample, val integration: IntegrationResult) : TripEvent

    data object StitchWindowExpired : TripEvent

    data object IdleTimeout : TripEvent

    data object Enabled : TripEvent

    data object Disabled : TripEvent

    /** Sent once at startup if [com.hopcape.odo.core.triptracker.port.TripSessionStore] held a snapshot. */
    data class SessionRestored(val snapshot: SessionSnapshot) : TripEvent

    /** Owner tapped "Pause" on the live notification (M5) — soft-pauses without ending the trip. */
    data object PauseRequested : TripEvent

    /** Owner tapped "Resume" on the live notification (M5). */
    data object ResumeRequested : TripEvent

    /** Owner tapped "Not driving" (M5) — ends the active trip without recording it. */
    data object DiscardRequested : TripEvent
}
