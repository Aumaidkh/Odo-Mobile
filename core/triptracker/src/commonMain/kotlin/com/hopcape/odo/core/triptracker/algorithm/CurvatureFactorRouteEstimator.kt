package com.hopcape.odo.core.triptracker.algorithm

import com.hopcape.odo.core.domain.trip.model.GeoPoint
import com.hopcape.odo.core.domain.trip.model.TripDistance
import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import com.hopcape.odo.core.triptracker.port.RouteDistanceEstimator
import kotlin.math.roundToLong

/**
 * Gap fill: a real route generally covers more ground than the straight line between
 * its ends, so the haversine chord is inflated by a curvature factor rather than used
 * as-is — city trips (< 3 km) bend more per kilometre than highway ones.
 *
 * The cheap default; a real routing adapter can replace this Koin binding later without
 * touching the engine (same pattern as `NoopSyncScheduler` → `WorkManagerSyncScheduler`).
 */
internal class CurvatureFactorRouteEstimator(
    private val config: TripTrackerConfig,
) : RouteDistanceEstimator {

    override suspend fun estimate(from: GeoPoint, to: GeoPoint): TripDistance? {
        val chordMeters = haversineMeters(from, to)
        val chordKm = chordMeters / 1_000.0
        val factor = if (chordKm < config.shortTripThresholdKm) {
            config.shortTripCurvatureFactor
        } else {
            config.longTripCurvatureFactor
        }
        return TripDistance.of((chordMeters * factor).roundToLong())
    }
}
