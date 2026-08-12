package com.hopcape.odo.feature.costtracker.presentation.runningcost

import arrow.core.getOrElse
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.SpendCategory
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.feature.costtracker.domain.model.CostPeriod
import com.hopcape.odo.feature.costtracker.presentation.state.Loadable
import com.hopcape.odo.feature.costtracker.resources.Res
import com.hopcape.odo.feature.costtracker.resources.ct_bar_range
import com.hopcape.odo.feature.costtracker.resources.ct_no_rate_readings
import com.hopcape.odo.feature.costtracker.resources.ct_period_range

/** A year of a well-used car — the state the previews render. */
internal fun previewRunningCost(): RunningCostUiState = RunningCostUiState(
    period = CostPeriod.Y1,
    content = Loadable.Ready(
        RunningCostContent(
            headline = CostHeadline.Rate(perKm = paise(460), trendPercent = 3, trendUp = true),
            distance = km(22_200),
            periodRange = UiText(Res.string.ct_period_range, listOf("Jul 2025", "Jun 2026")),
            spendBars = listOf(
                bar("Jul", "Aug", rupees(9_000)),
                bar("Sep", "Oct", rupees(19_000), highlighted = true),
                bar("Nov", "Dec", rupees(8_000)),
                bar("Jan", "Feb", rupees(7_500)),
                bar("Mar", "Apr", rupees(9_500)),
                bar("May", "Jun", rupees(18_000)),
            ),
            avgPerMonth = rupees(8_500),
            categories = listOf(
                CostCategoryRow(SpendCategory.FUEL, rupees(66_000), paise(297)),
                CostCategoryRow(SpendCategory.SERVICE, rupees(19_000), paise(86)),
                CostCategoryRow(SpendCategory.REPAIRS, rupees(6_200), paise(28)),
            ),
            totalSpent = rupees(91_200),
            fuelNote = FuelNote.Estimated(
                pricePerUnit = paise(10_440),
                unit = FuelUnit.LITRE,
                city = "Pune",
                ownersOwn = false,
                kmPerUnit = 15,
            ),
        ),
    ),
)

/** A car with one reading so far — no rate to quote, and the reason instead. */
internal fun previewRunningCostNoRate(): RunningCostUiState = RunningCostUiState(
    period = CostPeriod.M3,
    content = Loadable.Ready(
        RunningCostContent(
            headline = CostHeadline.NotEnoughYet(UiText(Res.string.ct_no_rate_readings)),
            distance = km(0),
            periodRange = UiText(Res.string.ct_period_range, listOf("May 2026", "Aug 2026")),
            spendBars = listOf(bar("Jun", "Jun", rupees(0)), bar("Jul", "Jul", rupees(3_400))),
            avgPerMonth = rupees(1_100),
            categories = listOf(CostCategoryRow(SpendCategory.SERVICE, rupees(3_400), perKm = null)),
            totalSpent = rupees(3_400),
            fuelNote = FuelNote.Missing,
        ),
    ),
)

private fun bar(from: String, to: String, amount: Amount, highlighted: Boolean = false) =
    SpendBar(UiText(Res.string.ct_bar_range, listOf(from, to)), amount, highlighted)

private fun rupees(amount: Long): Amount = paise(amount * 100)

private fun paise(amount: Long): Amount = Amount.of(amount).getOrElse { Amount.ZERO }

private fun km(distance: Int): Distance = Distance.of(distance).getOrElse { error("invalid preview distance") }
