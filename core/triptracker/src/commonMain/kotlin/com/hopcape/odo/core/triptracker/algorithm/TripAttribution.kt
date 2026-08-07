package com.hopcape.odo.core.triptracker.algorithm

import com.hopcape.odo.core.domain.trip.model.GeoPoint
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.odo.core.triptracker.config.TripTrackerConfig

/** [allowed] decides whether the trip starts at all; [confident] decides its status. */
internal data class AttributionResult(val allowed: Boolean, val confident: Boolean)

/**
 * The parked-location chain (§4.5) — what keeps a taxi, auto, or bus ride from being
 * recorded as the owner's car. A GPS-only trip is only attributed to the car if it
 * starts where the car was left; a Bluetooth-verified trip skips the check, since the
 * bond itself is stronger evidence than location.
 */
internal class TripAttribution(private val config: TripTrackerConfig) {

    fun evaluate(mode: TripMode, candidateStart: GeoPoint, parked: ParkedLocation?): AttributionResult = when {
        mode == TripMode.BT_VERIFIED -> AttributionResult(allowed = true, confident = true)
        // First run, nothing parked yet to check against — allowed, but never full
        // confidence until there is a parked location to compare the next start to.
        parked == null -> AttributionResult(allowed = true, confident = false)
        else -> {
            val distanceM = haversineMeters(candidateStart, parked.point)
            if (distanceM <= config.attributionRadiusM) {
                AttributionResult(allowed = true, confident = true)
            } else {
                AttributionResult(allowed = false, confident = false)
            }
        }
    }
}
