package com.hopcape.odo.core.domain.cost.model

import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import kotlin.math.abs

/**
 * What a car cost to run over one [window] — the numbers behind the running-cost screen
 * and the home tile.
 *
 * Built by
 * [RunningCostCalculator][com.hopcape.odo.core.domain.cost.analysis.RunningCostCalculator],
 * which is the app's only per-km math. [maintenanceSpend] is what the owner actually
 * logged; [fuelSpend] is estimated from the distance driven, so the surfaces that show it
 * have to say so.
 *
 * [perKm] and [shortfall] are opposites: exactly one of them is set. A rate needs enough
 * distance behind it to mean anything, and quoting ₹/km off forty kilometres would be the
 * false precision the PRD forbids — so below the threshold there is no rate at all, and
 * [shortfall] says why.
 */
data class RunningCost(
    val window: CostWindow,
    val kmDriven: Distance,
    val maintenanceSpend: Amount,
    val fuelSpend: Amount,
    val perKm: Amount?,
    val categories: List<CostBreakdown>,
    val shortfall: CostShortfall?,
) {
    /** Everything the car cost over the window — logged spend plus the fuel estimate. */
    val totalSpend: Amount get() = maintenanceSpend + fuelSpend

    /**
     * How this window's rate moved against [previous] — the trend badge.
     *
     * `null` when either window has no rate, or when the earlier one is free: a car that
     * cost nothing before cannot be a percentage cheaper now, and showing a number there
     * would invent a comparison.
     */
    fun trendAgainst(previous: RunningCost): CostTrend? {
        val now = perKm?.paise ?: return null
        val before = previous.perKm?.paise ?: return null
        if (before == 0L) return null
        return CostTrend(percentChange = roundedPercentChange(from = before, to = now))
    }
}

/** One "where it goes" row: what a bucket cost over the window, and its share of the rate. */
data class CostBreakdown(
    val category: SpendCategory,
    val spend: Amount,
    /** This bucket's contribution to the ₹/km rate; `null` whenever [RunningCost.perKm] is. */
    val perKm: Amount?,
)

/** Why a window has no ₹/km rate. */
sealed interface CostShortfall {

    /** The car has no odometer reading on or before the window's end — nothing to measure. */
    data object NoOdometerReadings : CostShortfall

    /**
     * The car moved less than [requiredKm] over the window (often because only one reading
     * exists so far). The screen asks for another log rather than showing a rate.
     */
    data class NotEnoughDistance(val kmDriven: Int, val requiredKm: Int) : CostShortfall
}

/**
 * Change in ₹/km against the window before — positive means running costlier.
 *
 * The badge shows [magnitude] with an arrow from [isUp]; the sign stays here so no caller
 * has to remember which direction is bad.
 */
data class CostTrend(val percentChange: Int) {
    val isUp: Boolean get() = percentChange > 0
    val magnitude: Int get() = abs(percentChange)
}

/** Percent change from [from] to [to], rounded to the nearest whole percent. */
private fun roundedPercentChange(from: Long, to: Long): Int {
    val diff = (to - from) * 100
    val rounded = if (diff >= 0) (diff + from / 2) / from else (diff - from / 2) / from
    return rounded.toInt()
}
