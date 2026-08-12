package com.hopcape.odo.core.domain.trip.analysis

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.trip.model.Trip
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.floor
import kotlin.time.Instant

/** The calibration ratio: derived-km = k × auto-tracked-km. [confident] is false until ≥2 manual readings gave at least one usable pair. */
data class Calibration(val k: Double, val confident: Boolean)

/** The latest pair's raw ratio diverged from the smoothed [Calibration.k] by more than 5%. */
data class OdometerDrift(val rawK: Double, val smoothedK: Double, val divergence: Double)

/**
 * Turns manual odometer readings and counted trips into a calibration ratio and a derived
 * "auto-odometer" reading (§4.7 of the trip tracker plan) — the same shape as
 * [OdometerTimeline][com.hopcape.odo.core.domain.servicelog.analysis.OdometerTimeline]:
 * pure functions the caller feeds [OdometerReading]s (from `ServiceLogRepository`) and
 * [Trip]s (from `TripRepository`).
 *
 * Every km figure here uses [TripDistance.km][com.hopcape.odo.core.domain.trip.model.TripDistance.km]'s
 * fractional precision, not [Distance]'s whole-km rounding — the ratio compounds rounding
 * error across many pairs, so only the final [derived] result floors to a whole km.
 */
object AutoOdometer {

    /** No manual-odometer distance is trusted more than 10% off the dash, either direction. */
    private const val MIN_K = 0.95
    private const val MAX_K = 1.10

    /** Absent a computable ratio (too few readings, or no counted trips in any window). */
    private const val DEFAULT_K = 1.0

    /**
     * EWMA smoothing weight for each new pair's raw ratio. Not given an explicit number by
     * the v4 report — a conservative, implementer-chosen default (revisit against field
     * data), same footing as [com.hopcape.odo.core.triptracker.config.TripTrackerConfig]'s
     * motion-debounce constants.
     */
    private const val EWMA_ALPHA = 0.3

    /** Surfaces [drift] once the latest pair's raw ratio diverges from the smoothed one by this much. */
    private const val DRIFT_THRESHOLD = 0.05

    fun calibration(readings: List<OdometerReading>, trips: List<Trip>): Calibration {
        val sorted = readings.sortedBy { it.date }
        if (sorted.size < 2) return Calibration(k = DEFAULT_K, confident = false)

        var smoothed: Double? = null
        for (i in 0 until sorted.size - 1) {
            val rawK = pairRawK(sorted[i], sorted[i + 1], trips) ?: continue
            smoothed = smoothed?.let { EWMA_ALPHA * rawK + (1 - EWMA_ALPHA) * it } ?: rawK
        }

        return Calibration(k = smoothed?.coerceIn(MIN_K, MAX_K) ?: DEFAULT_K, confident = smoothed != null)
    }

    /** [anchor] plus k × the counted distance since it, floored so the result never regresses. */
    fun derived(anchor: OdometerReading, tripsSince: List<Trip>, k: Calibration): Distance {
        val countedKm = tripsSince.filter { it.status.counted }.sumOf { it.distance.km }
        val derivedKm = anchor.odometer.km + k.k * countedKm
        val flooredKm = maxOf(anchor.odometer.km, floor(derivedKm).toInt())
        return Distance.of(flooredKm).getOrElse { error("AutoOdometer.derived produced a negative km: $flooredKm") }
    }

    /**
     * The car's current derived odometer: the highest known manual [OdometerReading] (the
     * anchor) plus the calibrated distance of every counted trip since it. `null` when there
     * are no manual readings yet — there is nothing to anchor from.
     *
     * The anchor is the reading with the **highest km**, not merely the most recently dated
     * one (ties go to the later date) — the same floor [OdometerTimeline]
     * [com.hopcape.odo.core.domain.servicelog.analysis.OdometerTimeline.validate] already
     * enforces when a new entry is logged (its `previous` is "the highest reading dated on
     * or before the candidate", which for a same-day candidate is exactly this). Anchoring on
     * a merely-newer-dated reading would let this figure regress below a value the app
     * already knows is real — e.g. a car onboarded at a placeholder baseline while real
     * service history already puts it higher, either from an earlier install or a sync pull.
     * A regressed pair like that also cannot produce a calibration ratio ([calibration]'s
     * `pairRawK` requires a positive manual delta), so it is silently ignored there too — the
     * two functions already agree on what the anomaly means, this just makes the anchor agree
     * as well.
     *
     * A trip is "since" the anchor by date, not by status — [derived] already filters to
     * counted trips internally. A later manual reading at or above the anchor's km becomes
     * the new anchor and everything before its day drops out of this sum on its own.
     *
     * [OdometerReading] only carries a date, not a time, so a trip on the **same** day as the
     * anchor is deliberately still counted (`>=`, not `>`): the far more common same-day case
     * is a fresh reading (an onboarding baseline, a just-typed log) followed by a same-day
     * drive, where the owner expects to see the new distance immediately rather than wait
     * until the next calendar day. The rarer inverse — a reading typed *after* already
     * driving, on the same day — can double-count that day's trip; there is no way to tell
     * the two apart without a reading timestamp this domain does not have.
     */
    fun current(readings: List<OdometerReading>, trips: List<Trip>): Distance? {
        val anchor = readings.maxWithOrNull(compareBy({ it.odometer.km }, { it.date })) ?: return null
        val anchorEpochDay = anchor.date.toEpochDays()
        val sinceAnchor = trips.filter { it.startedAt.epochDay() >= anchorEpochDay }
        return derived(anchor, sinceAnchor, calibration(readings, trips))
    }

    fun drift(readings: List<OdometerReading>, trips: List<Trip>): OdometerDrift? {
        val sorted = readings.sortedBy { it.date }
        if (sorted.size < 2) return null

        val rawK = pairRawK(sorted[sorted.size - 2], sorted[sorted.size - 1], trips) ?: return null
        val smoothedK = calibration(readings, trips).k
        val divergence = abs(rawK - smoothedK) / smoothedK
        return if (divergence > DRIFT_THRESHOLD) OdometerDrift(rawK, smoothedK, divergence) else null
    }

    /** `null` when the pair gives nothing usable — a non-positive manual delta or no counted trips. */
    private fun pairRawK(from: OdometerReading, to: OdometerReading, trips: List<Trip>): Double? {
        val manualDeltaKm = to.odometer.km - from.odometer.km
        val autoSumKm = trips.countedBetween(from.date, to.date).sumOf { it.distance.km }
        if (manualDeltaKm <= 0 || autoSumKm <= 0.0) return null
        return manualDeltaKm.toDouble() / autoSumKm
    }

    /** Counted trips that started after [after]'s day and on or before [upTo]'s day. */
    private fun List<Trip>.countedBetween(after: LocalDate, upTo: LocalDate): List<Trip> {
        val afterDay = after.toEpochDays()
        val upToDay = upTo.toEpochDays()
        return filter { it.status.counted && it.startedAt.epochDay() in (afterDay + 1)..upToDay }
    }

    private fun Instant.epochDay(): Long = toEpochMilliseconds().floorDiv(MILLIS_PER_DAY)

    private const val MILLIS_PER_DAY = 86_400_000L
}
