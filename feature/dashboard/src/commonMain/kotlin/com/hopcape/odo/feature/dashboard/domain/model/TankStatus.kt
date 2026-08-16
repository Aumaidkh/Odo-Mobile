package com.hopcape.odo.feature.dashboard.domain.model

import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import kotlinx.datetime.LocalDate

/**
 * How far the car has run since it was last filled, and what that fill was.
 *
 * What Home shows in place of a bare "Log a fill" button. The button asked the owner to do
 * something and gave nothing back; this answers the question they actually open the app with
 * after a pump — how far did that tank get me, and is it about time again.
 *
 * Every field is nullable because the answer genuinely may not exist. A car with no fills has
 * no last fill; a car whose fills carry no odometer reading has no distance; a car with fewer
 * than two fills has nothing to average a habit from. The card shows what it has and omits
 * what it does not, rather than printing a zero that reads like a measurement.
 */
data class TankStatus(
    /** Kilometres since the last fill, or `null` when there is nothing to measure from. */
    val sinceLastFill: Distance?,
    /** The day of the last fill. */
    val lastFilledOn: LocalDate?,
    /** What the last fill cost. */
    val lastAmount: Amount?,
    /** How much fuel it was, in thousandths of a unit — the storage unit on [FuelFill]. */
    val lastQuantityMilli: Long?,
    /** Litres, kilograms or kWh, so the quantity can be written with the right unit. */
    val lastUnit: FuelUnit?,
    /**
     * How far this car usually runs between fills.
     *
     * The average gap between consecutive fills that both carry an odometer reading. It is the
     * card's progress bar and its "usually you refill around 600 km" line — a habit read off
     * the owner's own history rather than a number from a table.
     */
    val typicalRange: Distance?,
) {

    /**
     * How far through a usual tank the car is, `0f`..`1f`, or `null` when either half of the
     * fraction is missing.
     *
     * Clamped at 1: running past the usual range is normal and common, and a bar that
     * overflowed would say something the data does not.
     */
    val progress: Float?
        get() {
            val since = sinceLastFill?.km ?: return null
            val range = typicalRange?.km?.takeIf { it > 0 } ?: return null
            return (since.toFloat() / range.toFloat()).coerceIn(0f, 1f)
        }

    /** Whether there is anything worth drawing — a card with no fill behind it says nothing. */
    val hasFill: Boolean get() = lastFilledOn != null

    companion object {

        /** A car with no fills logged yet. */
        val Empty = TankStatus(
            sinceLastFill = null,
            lastFilledOn = null,
            lastAmount = null,
            lastQuantityMilli = null,
            lastUnit = null,
            typicalRange = null,
        )

        /**
         * Read the tank's state from the car's fills and its current reading.
         *
         * [fills] is newest first, matching what the repository returns.
         *
         * The typical range is the mean gap between consecutive fills that both recorded an
         * odometer reading. Fills without one are skipped rather than treated as zero: a
         * detected fill reaches the owner at the pump, where the dashboard reading is the one
         * number out of reach, so a missing reading is expected and must not drag the average
         * to nothing.
         */
        fun of(fills: List<FuelFill>, currentOdometer: Distance?): TankStatus {
            val latest = fills.firstOrNull() ?: return Empty
            val since = latest.odometer?.km
                ?.let { last -> currentOdometer?.km?.minus(last)?.takeIf { it >= 0 } }
                ?.let { km -> Distance.of(km).getOrNull() }

            return TankStatus(
                sinceLastFill = since,
                lastFilledOn = latest.filledOn,
                lastAmount = latest.amount,
                lastQuantityMilli = latest.quantityMilli,
                lastUnit = latest.unit,
                typicalRange = typicalRange(fills),
            )
        }

        private fun typicalRange(fills: List<FuelFill>): Distance? {
            val readings = fills.mapNotNull { it.odometer?.km }
            if (readings.size < 2) return null
            // Newest first, so each gap is the earlier reading subtracted from the later one.
            val gaps = readings.zipWithNext { newer, older -> newer - older }.filter { it > 0 }
            if (gaps.isEmpty()) return null
            return Distance.of(gaps.average().toInt()).getOrNull()
        }
    }
}
