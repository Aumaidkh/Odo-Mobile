package com.hopcape.odo.core.triptracker

import com.hopcape.odo.core.domain.trip.model.TripMode
import kotlin.time.Instant

/** Snapshot of what the engine is doing. */
sealed interface TrackingStatus {
    data object Disabled : TrackingStatus

    /** Enabled and waiting for a trip trigger. [readiness] lists anything missing. */
    data class Standby(val readiness: TrackingReadiness) : TrackingStatus

    data class Tracking(val startedAt: Instant, val mode: TripMode) : TrackingStatus
}
