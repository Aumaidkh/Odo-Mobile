package com.hopcape.odo.core.triptracker.algorithm

import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import com.hopcape.odo.core.triptracker.model.LocationSample
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * What one fix contributed. [addedMeters] is definite distance from a Doppler or chord
 * reading this step; it is always `0` while a gap is open or closing — the gap-fill
 * metres come from [RouteDistanceEstimator][com.hopcape.odo.core.triptracker.port.RouteDistanceEstimator],
 * a suspend call the integrator itself cannot make, so [gapClosed] only hands the engine
 * the two boundary fixes to estimate between.
 */
internal data class IntegrationResult(
    val addedMeters: Long,
    val gapOpened: Boolean,
    val gapClosed: GapClosed?,
)

/** The last good fix before a gap opened, and the first good fix after it closed. */
internal data class GapClosed(val from: LocationSample, val to: LocationSample)

/**
 * Folds the 1 Hz fix stream into distance while TRACKING (§4.3). Doppler speed is
 * preferred; a haversine chord is the fallback; anything worse opens a gap that closes,
 * once, on the next fix good enough to stand on its own — never per poll, so the v3
 * repeated-dead-reckoning bug is structurally impossible here.
 */
internal class DistanceIntegrator(private val config: TripTrackerConfig) {

    /** The last fix trusted enough to measure from — only ever a Doppler/chord-quality fix. */
    private var lastGood: LocationSample? = null
    private var inGap = false

    fun accept(sample: LocationSample): IntegrationResult {
        val prev = lastGood

        if (prev == null) {
            lastGood = sample
            return IntegrationResult(addedMeters = 0, gapOpened = false, gapClosed = null)
        }

        if (inGap) {
            // No trustworthy delta to lean on mid-gap — only this sample's own quality
            // decides whether the gap closes.
            if (!hasDopplerQuality(sample) && !hasChordQuality(sample)) {
                return IntegrationResult(addedMeters = 0, gapOpened = false, gapClosed = null)
            }
            inGap = false
            lastGood = sample
            return IntegrationResult(addedMeters = 0, gapOpened = false, gapClosed = GapClosed(prev, sample))
        }

        val added = dopplerDeltaMeters(prev, sample) ?: chordDeltaMeters(prev, sample)
        if (added != null) {
            lastGood = sample
            return IntegrationResult(addedMeters = added, gapOpened = false, gapClosed = null)
        }

        inGap = true
        return IntegrationResult(addedMeters = 0, gapOpened = true, gapClosed = null)
    }

    private fun hasDopplerQuality(sample: LocationSample): Boolean =
        sample.speedMps != null && sample.accuracyM <= config.dopplerMaxAccuracyM

    private fun hasChordQuality(sample: LocationSample): Boolean =
        sample.accuracyM <= config.chordMaxAccuracyM

    private fun dopplerDeltaMeters(prev: LocationSample, sample: LocationSample): Long? {
        val speed = sample.speedMps ?: return null
        if (!hasDopplerQuality(sample)) return null
        val dtSeconds = (sample.elapsed - prev.elapsed).inWholeMilliseconds / 1000.0
        if (dtSeconds <= 0) return null
        val prevSpeed = prev.speedMps ?: speed
        val impliedAcceleration = abs(speed - prevSpeed) / dtSeconds
        if (impliedAcceleration > config.dopplerMaxPlausibleAccelerationMps2) return null
        return (speed * dtSeconds).roundToLong()
    }

    /**
     * The chord is gated on the combined uncertainty of both fixes (v3 bug #6 fixed): a
     * chord shorter than that is indistinguishable from GPS noise around a stationary
     * point, so it counts as zero movement rather than a spurious drift.
     */
    private fun chordDeltaMeters(prev: LocationSample, sample: LocationSample): Long? {
        if (!hasChordQuality(sample)) return null
        val chordMeters = haversineMeters(prev.lat, prev.lon, sample.lat, sample.lon)
        val combinedUncertainty = prev.accuracyM + sample.accuracyM
        if (chordMeters < combinedUncertainty) return 0L
        return chordMeters.roundToLong()
    }
}
