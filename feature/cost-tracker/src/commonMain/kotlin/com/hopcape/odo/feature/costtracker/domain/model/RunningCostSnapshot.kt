package com.hopcape.odo.feature.costtracker.domain.model

import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.cost.fuel.FuelPrice
import com.hopcape.odo.core.domain.cost.model.CostTrend
import com.hopcape.odo.core.domain.cost.model.CostWindow
import com.hopcape.odo.core.domain.cost.model.RunningCost
import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.datetime.LocalDate

/**
 * Everything the running-cost screen shows, computed together from one read of the car,
 * its logs and its odometer readings.
 *
 * One snapshot rather than several streams, because every figure on the screen is a view
 * of the same window: a headline rate taken from one read and a chart taken from another
 * would disagree the moment a log is added between them.
 *
 * [previous] is the window immediately before [cost]'s, and exists only to produce [trend].
 * [fuelPrice] is carried so the screen can say where the fuel estimate came from — the
 * owner's own rate or Odo's figure for their city, and how old it is.
 */
internal data class RunningCostSnapshot(
    val car: Car?,
    val period: CostPeriod,
    val cost: RunningCost,
    val previous: RunningCost,
    val buckets: List<SpendBucket>,
    val fuelPrice: FuelPrice?,
    val today: LocalDate,
) {
    /** The window everything here was computed over. */
    val window: CostWindow get() = cost.window

    /** How the rate moved against the window before; `null` when either has no rate. */
    val trend: CostTrend? get() = cost.trendAgainst(previous)

    /**
     * Whether the fuel half is missing entirely — no city set, or no price known for it.
     * The screen offers the owner their own rate instead of quietly showing maintenance
     * as if it were the whole cost of running the car.
     */
    val fuelEstimateMissing: Boolean get() = fuelPrice == null
}

/** One bar of the spend chart: what the car cost over a slice of the period. */
internal data class SpendBucket(
    val window: CostWindow,
    val spend: Amount,
)
