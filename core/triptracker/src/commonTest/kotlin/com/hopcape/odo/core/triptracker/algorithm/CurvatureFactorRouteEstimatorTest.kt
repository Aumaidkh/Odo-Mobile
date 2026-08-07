package com.hopcape.odo.core.triptracker.algorithm

import com.hopcape.odo.core.domain.trip.model.GeoPoint
import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import kotlinx.coroutines.test.runTest
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals

class CurvatureFactorRouteEstimatorTest {

    private val config = TripTrackerConfig()
    private val estimator = CurvatureFactorRouteEstimator(config)

    @Test
    fun shortTrip_usesTheShortFactor() = runTest {
        val from = GeoPoint.of(19.00, 72.80).getOrNull()!!
        val to = GeoPoint.of(19.01, 72.80).getOrNull()!! // ~1.1 km chord, under the 3 km threshold
        val chordMeters = haversineMeters(from, to)

        val result = estimator.estimate(from, to)!!

        assertEquals((chordMeters * config.shortTripCurvatureFactor).roundToLong(), result.meters)
    }

    @Test
    fun longTrip_usesTheLongFactor() = runTest {
        val from = GeoPoint.of(19.00, 72.80).getOrNull()!!
        val to = GeoPoint.of(19.10, 72.80).getOrNull()!! // ~11 km chord, over the 3 km threshold
        val chordMeters = haversineMeters(from, to)

        val result = estimator.estimate(from, to)!!

        assertEquals((chordMeters * config.longTripCurvatureFactor).roundToLong(), result.meters)
    }
}
