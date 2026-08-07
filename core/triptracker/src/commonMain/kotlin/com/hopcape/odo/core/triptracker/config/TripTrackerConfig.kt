package com.hopcape.odo.core.triptracker.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Every tunable threshold the engine uses, gathered in one place so a future remote-config
 * adapter can construct this without touching the engine (v4's `RemoteConfig.get(...)`
 * becomes this data class plus one Koin binding).
 *
 * Defaults are the v4 analysis report's numbers (2026-08-07) where the report states one
 * explicitly. The two motion-debounce constants are not spelled out there — they are
 * conservative, implementer-chosen defaults, called out so they get revisited against
 * real field data rather than mistaken for a v4 number.
 */
internal data class TripTrackerConfig(
    // §4.2 state machine timings
    val gpsStartSustainedMotion: Duration = 45.seconds,
    val gpsStopStillOrWalking: Duration = 3.minutes,
    val stitchWindow: Duration = 5.minutes,
    val idleSoftPauseThreshold: Duration = 20.minutes,

    // §4.3 distance measurement quality gates
    val dopplerMaxAccuracyM: Float = 30f,
    val dopplerMaxPlausibleAccelerationMps2: Float = 4f,
    val chordMaxAccuracyM: Float = 50f,

    // §4.4 validation ladder
    val recordedMinDistanceM: Long = 1_000,
    val confirmMinDistanceM: Long = 300,
    val confirmMinDuration: Duration = 2.minutes,
    val discardBelowDistanceM: Long = 100,
    val estimatedPortionDowngradeRatio: Double = 0.4,

    // §4.5 attribution
    val attributionRadiusM: Double = 150.0,

    // Gap-fill route estimate (CurvatureFactorRouteEstimator, S3)
    val shortTripCurvatureFactor: Double = 1.25,
    val longTripCurvatureFactor: Double = 1.05,
    val shortTripThresholdKm: Double = 3.0,

    // Motion debouncer — not given explicit numbers by the v4 report; conservative defaults.
    val motionDebounceConsecutiveReadings: Int = 3,
    val motionDebounceMinConfidence: Int = 75,
)
