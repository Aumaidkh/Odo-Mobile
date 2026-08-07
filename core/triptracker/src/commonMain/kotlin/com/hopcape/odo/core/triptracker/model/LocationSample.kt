package com.hopcape.odo.core.triptracker.model

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * One location fix. Carries both a wall-clock [at] (for display/storage) and a
 * monotonic [elapsed] (for duration math immune to clock jumps).
 */
internal data class LocationSample(
    val at: Instant,
    val elapsed: Duration,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val speedMps: Float? = null,
    val speedAccuracyMps: Float? = null,
    val bearingDeg: Float? = null,
)
