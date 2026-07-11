package com.hopcape.odo.feature.costtracker.presentation.runningcost

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance

/** The window the running-cost figures are computed over. */
internal enum class CostPeriod { M3, M6, Y1 }

/** The spend categories the running cost breaks down into (drives label + colour). */
internal enum class CostCategory { FUEL, SERVICE, INSURANCE, REPAIRS }

/** One bar in the "spend by month" chart — a bucket's spend, optionally highlighted (a peak). */
internal data class SpendBar(
    val label: String,
    val amount: Amount,
    val highlighted: Boolean = false,
)

/** One "where it goes" row — a category's total spend and its per-km contribution. */
internal data class CostCategoryRow(
    val category: CostCategory,
    val amount: Amount,
    val perKm: Amount,
)

/**
 * Display state for the "Running cost" screen — the per-km cost tracker.
 *
 * All money is [Amount] (integer paise): the headline [costPerKm] is a paise/km rate,
 * category [CostCategoryRow.amount] are whole-rupee totals. Rupees (and the per-km
 * decimals) are rendered only in the UI. The bar/track proportions are derived from
 * the amounts, so nothing here carries a pre-computed ratio.
 */
internal data class RunningCostUiState(
    val costPerKm: Amount,
    val trendPercent: Int,
    val trendUp: Boolean,
    val distance: Distance,
    val periodRange: String,
    val period: CostPeriod,
    val spendBars: List<SpendBar>,
    val avgPerMonth: Amount,
    val categories: List<CostCategoryRow>,
    val totalSpent: Amount,
)

/** Sample state for previews and the pre-ViewModel route host (mirrors the mockup). */
internal fun sampleRunningCost(period: CostPeriod = CostPeriod.Y1): RunningCostUiState = RunningCostUiState(
    costPerKm = paise(460),
    trendPercent = 3,
    trendUp = true,
    distance = km(22_200),
    periodRange = "Jul 2025–Jun 2026",
    period = period,
    spendBars = listOf(
        SpendBar("J–A", rupees(9_000)),
        SpendBar("S–O", rupees(19_000), highlighted = true),
        SpendBar("N–D", rupees(8_000)),
        SpendBar("J–F", rupees(7_500)),
        SpendBar("M–A", rupees(9_500)),
        SpendBar("M–J", rupees(18_000), highlighted = true),
    ),
    avgPerMonth = rupees(8_500),
    categories = listOf(
        CostCategoryRow(CostCategory.FUEL, rupees(66_000), paise(297)),
        CostCategoryRow(CostCategory.SERVICE, rupees(19_000), paise(86)),
        CostCategoryRow(CostCategory.INSURANCE, rupees(10_800), paise(49)),
        CostCategoryRow(CostCategory.REPAIRS, rupees(6_200), paise(28)),
    ),
    totalSpent = rupees(102_000),
)

private fun rupees(amount: Long): Amount = paise(amount * 100)
private fun paise(amount: Long): Amount = Amount.of(amount).getOrElse { Amount.ZERO }
private fun km(distance: Int): Distance = Distance.of(distance).getOrNull() ?: error("invalid sample distance")
