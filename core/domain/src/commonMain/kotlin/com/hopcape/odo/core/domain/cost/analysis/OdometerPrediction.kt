package com.hopcape.odo.core.domain.cost.analysis

import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.currentReading
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * Guesses what a car's odometer reads today, from how fast it has been moving.
 *
 * This exists so the confirm step can open with a number already on the drum. An owner at a
 * pump can check a pre-spun figure against their dashboard in a second; typing six digits
 * from nothing is the slowest part of logging a fill, and it is why fills go unlogged.
 *
 * The guess is always labelled as a guess. [Prediction.predicted] being true is what makes
 * the screen show "check it", and nothing here is ever written without the owner seeing it.
 *
 * A pure calculator, like [RunningCostCalculator] and
 * [OdometerTimeline][com.hopcape.odo.core.domain.servicelog.analysis.OdometerTimeline]: it
 * takes readings and a date, and returns a number. It lives in `:core:domain` because both
 * the refuel confirm step and the garage's odometer sheet want the same figure, and a
 * feature may not import another feature.
 */
object OdometerPrediction {

    /**
     * How much history is needed before a rate is worth using.
     *
     * Two readings a day apart can imply 400 km a day, which is arithmetic rather than a
     * pattern. Two weeks is enough for a commute to average out.
     */
    const val MIN_DAYS_FOR_RATE: Int = 14

    /**
     * The furthest ahead of the last reading a prediction may sit.
     *
     * A car that has not been logged for a year would otherwise open the drum tens of
     * thousands of kilometres ahead of its dashboard, and an owner correcting a wildly wrong
     * number is worse off than one typing into a field that starts at their last known
     * reading. Past this, the prediction falls back to the last reading itself.
     */
    const val MAX_PROJECTION_KM: Int = 5_000

    /**
     * What the car most likely reads on [today].
     *
     * Returns `null` only when there is no reading at all to anchor on — then there is
     * nothing to prefill and the field starts empty.
     *
     * With one reading, or with a history too short to imply a rate, the last reading comes
     * back unchanged and [Prediction.predicted] is false: it is a real number the owner
     * gave, not a guess, and the screen should not ask them to double-check it as though it
     * were one.
     */
    fun forToday(readings: List<OdometerReading>, today: LocalDate): Prediction? {
        val latest = readings.currentReading() ?: return null
        val perDay = kmPerDay(readings) ?: return Prediction(latest.odometer.km, predicted = false)

        val elapsed = latest.date.daysUntil(today)
        if (elapsed <= 0) return Prediction(latest.odometer.km, predicted = false)

        val projected = (perDay * elapsed).toInt().coerceAtMost(MAX_PROJECTION_KM)
        if (projected <= 0) return Prediction(latest.odometer.km, predicted = false)
        return Prediction(latest.odometer.km + projected, predicted = true)
    }

    /**
     * The car's average kilometres a day, or `null` when its history is too thin to say.
     *
     * Measured across the whole span rather than the last two readings: a single short gap
     * between two logs in one week says nothing about a car, and the long view is what the
     * projection above is exposed to.
     *
     * Readings are compared by kilometres, not by date order, so a row entered out of order
     * cannot produce a negative rate.
     */
    fun kmPerDay(readings: List<OdometerReading>): Double? {
        if (readings.size < 2) return null
        val earliest = readings.minWithOrNull(compareBy({ it.date }, { it.odometer.km })) ?: return null
        val latest = readings.currentReading() ?: return null

        val days = earliest.date.daysUntil(latest.date)
        if (days < MIN_DAYS_FOR_RATE) return null

        val km = latest.odometer.km - earliest.odometer.km
        if (km <= 0) return null
        return km.toDouble() / days
    }
}

/**
 * A predicted odometer reading.
 *
 * [predicted] is the part that matters to the screen: false means [km] is the owner's own
 * last reading carried forward, true means Odo worked it out and they should check it.
 */
data class Prediction(
    val km: Int,
    val predicted: Boolean,
)
