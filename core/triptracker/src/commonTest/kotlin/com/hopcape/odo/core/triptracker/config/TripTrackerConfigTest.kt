package com.hopcape.odo.core.triptracker.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TripTrackerConfigTest {

    private val config = TripTrackerConfig()

    @Test
    fun stateMachineTimings_matchPlanDefaults() {
        assertEquals(45.seconds, config.gpsStartSustainedMotion)
        assertEquals(3.minutes, config.gpsStopStillOrWalking)
        assertEquals(5.minutes, config.stitchWindow)
        assertEquals(20.minutes, config.idleSoftPauseThreshold)
    }

    @Test
    fun distanceQualityGates_matchPlanDefaults() {
        assertEquals(30f, config.dopplerMaxAccuracyM)
        assertEquals(4f, config.dopplerMaxPlausibleAccelerationMps2)
        assertEquals(50f, config.chordMaxAccuracyM)
    }

    @Test
    fun validationLadder_matchesPlanDefaults() {
        assertEquals(1_000L, config.recordedMinDistanceM)
        assertEquals(300L, config.confirmMinDistanceM)
        assertEquals(2.minutes, config.confirmMinDuration)
        assertEquals(100L, config.discardBelowDistanceM)
        assertEquals(0.4, config.estimatedPortionDowngradeRatio)
    }

    @Test
    fun attribution_matchesPlanDefault() {
        assertEquals(150.0, config.attributionRadiusM)
    }

    @Test
    fun routeEstimateCurvatureFactors_matchPlanDefaults() {
        assertEquals(1.25, config.shortTripCurvatureFactor)
        assertEquals(1.05, config.longTripCurvatureFactor)
        assertEquals(3.0, config.shortTripThresholdKm)
    }
}
