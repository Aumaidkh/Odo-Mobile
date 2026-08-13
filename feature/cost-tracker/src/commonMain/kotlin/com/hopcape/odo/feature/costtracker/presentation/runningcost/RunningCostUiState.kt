package com.hopcape.odo.feature.costtracker.presentation.runningcost

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyUnit
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.SpendCategory
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.feature.costtracker.domain.model.CostPeriod
import com.hopcape.odo.feature.costtracker.presentation.state.Loadable

/** One bar in the spend chart — a slice of the period, highlighted when it is the peak. */
@Immutable
internal data class SpendBar(
    val label: UiText,
    val amount: Amount,
    val highlighted: Boolean = false,
)

/** One "where it goes" row — a category's total spend and its per-km contribution. */
@Immutable
internal data class CostCategoryRow(
    val category: SpendCategory,
    val amount: Amount,
    val perKm: Amount?,
)

/**
 * What the screen says about the fuel half of the cost.
 *
 * Fuel is never logged — the PRD drops that as friction — so it is worked out from the
 * distance driven, a price and an assumed efficiency. The owner has to be able to tell
 * which part of their ₹/km was estimated, and off what.
 */
@Immutable
internal sealed interface FuelNote {

    /** No price for the owner's city, or no city at all: the figures carry no fuel. */
    data object Missing : FuelNote

    /**
     * Fuel is included, at [pricePerUnit] per [unit]. [ownersOwn] is the rate the owner
     * set themselves, which is worth saying — it is the number they can trust most.
     */
    @Immutable
    data class Estimated(
        val pricePerUnit: Amount,
        val unit: FuelUnit,
        val city: String?,
        val ownersOwn: Boolean,
        /** The assumed distance per unit of fuel the estimate was built on. */
        val kmPerUnit: Int,
    ) : FuelNote
}

/** The headline figure, which a car earns only once it has been driven far enough. */
@Immutable
internal sealed interface CostHeadline {

    /** The rate, and how it moved against the window before (`null` = nothing to compare). */
    @Immutable
    data class Rate(val perKm: Amount, val trendPercent: Int?, val trendUp: Boolean) : CostHeadline

    /**
     * No rate, and why. Shown instead of a number, never alongside one: a ₹/km taken off
     * forty kilometres is arithmetic, not information (the PRD's no-false-precision rule).
     */
    @Immutable
    data class NotEnoughYet(val message: UiText) : CostHeadline
}

/**
 * What the running-cost screen renders once the read lands.
 *
 * All money is [Amount] (integer paise); rupees appear only where the UI formats them.
 * [fuelNote] is never null — fuel is never logged, so its share is always an estimate and
 * the screen always says so, including when there is no estimate to show.
 */
@Immutable
internal data class RunningCostContent(
    val headline: CostHeadline,
    val distance: Distance,
    val periodRange: UiText,
    val spendBars: List<SpendBar>,
    val avgPerMonth: Amount,
    val categories: List<CostCategoryRow>,
    val totalSpent: Amount,
    val fuelNote: FuelNote,
)

/** Screen state: the period chips stay live while the figures under them are read. */
@Immutable
internal data class RunningCostUiState(
    val period: CostPeriod = CostPeriod.Y1,
    val content: Loadable<RunningCostContent> = Loadable.Loading,
    /** How the owner writes fuel efficiency — their setting, not this screen's choice. */
    val fuelEfficiencyUnit: FuelEfficiencyUnit = FuelEfficiencyUnit.Default,
    /** No active car — setup incomplete or the car was removed. A nudge, not a spinner:
     *  there is no read in flight, so a loader would spin forever. */
    val noCar: Boolean = false,
)
