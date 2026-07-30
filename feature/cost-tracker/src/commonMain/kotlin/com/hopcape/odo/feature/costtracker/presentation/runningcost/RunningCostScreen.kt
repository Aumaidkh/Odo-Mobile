package com.hopcape.odo.feature.costtracker.presentation.runningcost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoChip
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCaretUpFilled
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.formatKm
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.core.domain.shared.formatRupeesDecimal
import com.hopcape.odo.feature.costtracker.resources.Res
import com.hopcape.odo.feature.costtracker.resources.ct_across
import com.hopcape.odo.feature.costtracker.resources.ct_avg_month
import com.hopcape.odo.feature.costtracker.resources.ct_avg_per_month
import com.hopcape.odo.feature.costtracker.resources.ct_cat_fuel
import com.hopcape.odo.feature.costtracker.resources.ct_cat_insurance
import com.hopcape.odo.feature.costtracker.resources.ct_cat_repairs
import com.hopcape.odo.feature.costtracker.resources.ct_cat_service
import com.hopcape.odo.feature.costtracker.resources.ct_cost_per_km_label
import com.hopcape.odo.feature.costtracker.resources.ct_distance_driven
import com.hopcape.odo.feature.costtracker.resources.ct_per_km_rate
import com.hopcape.odo.feature.costtracker.resources.ct_per_km_suffix
import com.hopcape.odo.feature.costtracker.resources.ct_period_1y
import com.hopcape.odo.feature.costtracker.resources.ct_period_3m
import com.hopcape.odo.feature.costtracker.resources.ct_period_6m
import com.hopcape.odo.feature.costtracker.resources.ct_spend_by_month
import com.hopcape.odo.feature.costtracker.resources.ct_summary
import com.hopcape.odo.feature.costtracker.resources.ct_title
import com.hopcape.odo.feature.costtracker.resources.ct_total_spent
import com.hopcape.odo.feature.costtracker.resources.ct_where_it_goes
import org.jetbrains.compose.resources.stringResource

/** Insurance's chart colour — no semantic blue token exists, so it lives with the chart. */
private val InsuranceBlue = Color(0xFF4E86E8)
private val BarShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
private val MONTH_BAR_HEIGHT = 88.dp

/**
 * The "Running cost" screen — the per-km cost tracker. Leads with the headline
 * cost/km + trend, a period selector, a spend-by-month bar chart, the "where it goes"
 * category breakdown (each with its per-km share + a proportional bar), and a summary.
 *
 * State-free: renders [state] and forwards the period change. The real figures (from
 * the local ledger aggregated per car) land in M2.
 */
@Composable
internal fun RunningCostScreen(
    state: RunningCostUiState,
    onPeriodChange: (CostPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(modifier = modifier, title = stringResource(Res.string.ct_title)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            CostHeroCard(state)
            PeriodSelector(selected = state.period, onChange = onPeriodChange)
            SpendByMonthCard(bars = state.spendBars, avgPerMonth = state.avgPerMonth)
            SectionLabel(stringResource(Res.string.ct_where_it_goes))
            CategoryCard(categories = state.categories)
            SectionLabel(stringResource(Res.string.ct_summary))
            SummaryCard(state)
        }
    }
}

@Composable
private fun CostHeroCard(state: RunningCostUiState) {
    OdoCard {
        OdoText(stringResource(Res.string.ct_cost_per_km_label), style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                OdoText(state.costPerKm.formatRupeesDecimal(), style = OdoTheme.typography.display, modifier = Modifier.alignByBaseline())
                OdoText(stringResource(Res.string.ct_per_km_suffix), style = OdoTheme.typography.heading, color = OdoTheme.colors.textDim, modifier = Modifier.alignByBaseline())
            }
            TrendBadge(percent = state.trendPercent, up = state.trendUp)
        }
        OdoText(
            stringResource(Res.string.ct_across, state.distance.formatKm(), state.periodRange),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
    }
}

@Composable
private fun TrendBadge(percent: Int, up: Boolean) {
    OdoBadge(
        text = "$percent%",
        tone = if (up) OdoBadgeTone.Danger else OdoBadgeTone.Success,
        leadingIcon = {
            OdoIcon(
                IcCaretUpFilled,
                contentDescription = null,
                modifier = if (up) Modifier else Modifier.rotate(180f),
                size = OdoTheme.iconSizes.small,
            )
        },
    )
}

@Composable
private fun PeriodSelector(selected: CostPeriod, onChange: (CostPeriod) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        CostPeriod.entries.forEach { period ->
            OdoChip(label = periodLabel(period), selected = period == selected, onClick = { onChange(period) })
        }
    }
}

@Composable
private fun SpendByMonthCard(bars: List<SpendBar>, avgPerMonth: Amount) {
    OdoCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OdoText(stringResource(Res.string.ct_spend_by_month), style = OdoTheme.typography.heading)
            OdoText(
                stringResource(Res.string.ct_avg_per_month, avgPerMonth.formatRupees()),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
        val max = bars.maxOfOrNull { it.amount.paise }?.coerceAtLeast(1) ?: 1
        Row(Modifier.fillMaxWidth().padding(top = OdoTheme.spacing.sm), horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            bars.forEach { bar -> SpendBarColumn(bar = bar, max = max, modifier = Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SpendBarColumn(bar: SpendBar, max: Long, modifier: Modifier = Modifier) {
    val fraction = (bar.amount.paise.toFloat() / max).coerceIn(0.08f, 1f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        Box(Modifier.fillMaxWidth().height(MONTH_BAR_HEIGHT), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier
                    .fillMaxWidth(0.72f)
                    .height(MONTH_BAR_HEIGHT * fraction)
                    .clip(BarShape)
                    .background(if (bar.highlighted) OdoTheme.colors.accent else OdoTheme.colors.surfaceRaised),
            )
        }
        OdoText(bar.label, style = OdoTheme.typography.caption, color = OdoTheme.colors.textDim)
    }
}

@Composable
private fun CategoryCard(categories: List<CostCategoryRow>) {
    OdoCard(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
        val max = categories.maxOfOrNull { it.amount.paise }?.coerceAtLeast(1) ?: 1
        categories.forEach { row -> CategoryRow(row = row, max = max) }
    }
}

@Composable
private fun CategoryRow(row: CostCategoryRow, max: Long) {
    val color = categoryColor(row.category)
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            CategoryChip(color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(categoryLabel(row.category), style = OdoTheme.typography.heading)
                OdoText(
                    stringResource(Res.string.ct_per_km_rate, row.perKm.formatRupeesDecimal()),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
            OdoText(row.amount.formatRupees(), style = OdoTheme.typography.heading)
        }
        ProgressTrack(fraction = (row.amount.paise.toFloat() / max).coerceIn(0.04f, 1f), color = color)
    }
}

@Composable
private fun CategoryChip(color: Color) {
    Box(
        Modifier.size(34.dp).clip(OdoTheme.shapes.small).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

@Composable
private fun ProgressTrack(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(OdoTheme.shapes.pill).background(OdoTheme.colors.surfaceRaised)) {
        Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(OdoTheme.shapes.pill).background(color))
    }
}

@Composable
private fun SummaryCard(state: RunningCostUiState) {
    OdoCard(
        contentPadding = PaddingValues(horizontal = OdoTheme.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        SummaryRow(stringResource(Res.string.ct_total_spent), state.totalSpent.formatRupees())
        HorizontalDivider(color = OdoTheme.colors.border)
        SummaryRow(stringResource(Res.string.ct_distance_driven), state.distance.formatKm())
        HorizontalDivider(color = OdoTheme.colors.border)
        SummaryRow(stringResource(Res.string.ct_avg_month), state.avgPerMonth.formatRupees())
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoText(label, style = OdoTheme.typography.body, color = OdoTheme.colors.textDim)
        OdoText(value, style = OdoTheme.typography.heading)
    }
}

@Composable
private fun SectionLabel(text: String) {
    OdoText(text, style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
}

@Composable
private fun categoryColor(category: CostCategory): Color = when (category) {
    CostCategory.FUEL -> OdoTheme.colors.accent
    CostCategory.SERVICE -> OdoTheme.colors.success
    CostCategory.INSURANCE -> InsuranceBlue
    CostCategory.REPAIRS -> OdoTheme.colors.warning
}

@Composable
private fun categoryLabel(category: CostCategory): String = stringResource(
    when (category) {
        CostCategory.FUEL -> Res.string.ct_cat_fuel
        CostCategory.SERVICE -> Res.string.ct_cat_service
        CostCategory.INSURANCE -> Res.string.ct_cat_insurance
        CostCategory.REPAIRS -> Res.string.ct_cat_repairs
    },
)

@Composable
private fun periodLabel(period: CostPeriod): String = stringResource(
    when (period) {
        CostPeriod.M3 -> Res.string.ct_period_3m
        CostPeriod.M6 -> Res.string.ct_period_6m
        CostPeriod.Y1 -> Res.string.ct_period_1y
    },
)

@OdoThemePreviews
@Composable
private fun RunningCostScreenPreview() = OdoPreview(padded = false) {
    RunningCostScreen(state = sampleRunningCost(), onPeriodChange = {})
}
