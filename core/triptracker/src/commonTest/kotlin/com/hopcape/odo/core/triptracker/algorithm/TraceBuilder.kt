package com.hopcape.odo.core.triptracker.algorithm

import com.hopcape.odo.core.triptracker.model.LocationSample
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Builds synthetic 1 Hz [LocationSample] streams for the algorithm tests — a straight
 * line (or a short polyline) walked at a fixed speed and accuracy, with individual
 * samples overridable so a test can inject a dropout, a jitter, or a stationary cluster.
 */
internal object TraceBuilder {

    private val START: Instant = Instant.fromEpochSeconds(1_700_000_000)

    /** One fix per second along the straight line between [from] and [to]. */
    fun straightLine(
        from: Pair<Double, Double>,
        to: Pair<Double, Double>,
        speedMps: Float,
        accuracyM: Float,
        samples: Int,
    ): List<LocationSample> = (0..samples).map { tick ->
        val fraction = tick.toDouble() / samples
        val lat = from.first + (to.first - from.first) * fraction
        val lon = from.second + (to.second - from.second) * fraction
        sample(tick = tick, lat = lat, lon = lon, accuracyM = accuracyM, speedMps = speedMps)
    }

    /** [count] fixes clustered on the same point — a car parked with GPS drift. */
    fun stationaryCluster(
        at: Pair<Double, Double>,
        accuracyM: Float,
        count: Int,
        jitterMeters: Double = 5.0,
    ): List<LocationSample> {
        // A tiny, deterministic wobble around `at` — well inside a normal GPS accuracy
        // circle, never in the same spot twice.
        val jitterDegrees = jitterMeters / 111_000.0
        return (0 until count).map { tick ->
            val wobble = if (tick % 2 == 0) jitterDegrees else -jitterDegrees
            sample(
                tick = tick,
                lat = at.first + wobble,
                lon = at.second,
                accuracyM = accuracyM,
                speedMps = 0f,
            )
        }
    }

    fun sample(
        tick: Int,
        lat: Double,
        lon: Double,
        accuracyM: Float,
        speedMps: Float? = null,
        speedAccuracyMps: Float? = null,
        bearingDeg: Float? = null,
    ): LocationSample = LocationSample(
        at = START + tick.seconds,
        elapsed = tick.seconds,
        lat = lat,
        lon = lon,
        accuracyM = accuracyM,
        speedMps = speedMps,
        speedAccuracyMps = speedAccuracyMps,
        bearingDeg = bearingDeg,
    )
}
