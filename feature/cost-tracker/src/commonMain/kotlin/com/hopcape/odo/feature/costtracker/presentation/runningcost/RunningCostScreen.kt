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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arrow.core.getOrElse
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import com.hopcape.odo.core.designsystem.icons.IcLockFilled
import com.hopcape.odo.feature.costtracker.resources.ct_locked_title
import com.hopcape.odo.feature.costtracker.resources.ct_locked_body
import com.hopcape.odo.feature.costtracker.resources.ct_locked_cta
import androidx.compose.ui.text.style.TextAlign
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCoachMark
import com.hopcape.odo.core.designsystem.component.coachMarkAnchor
import com.hopcape.odo.core.designsystem.component.rememberCoachMarkAnchorState
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoChip
import com.hopcape.odo.core.designsystem.component.OdoDistanceUnit
import com.hopcape.odo.core.designsystem.component.OdoEmptyState
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCar
import com.hopcape.odo.core.designsystem.icons.IcCaretUpFilled
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.core.designsystem.units.OdoDistanceFormat
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyUnit
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.fuel.format
import com.hopcape.odo.core.domain.cost.model.SpendCategory
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.core.domain.shared.formatRupeesDecimal
import com.hopcape.odo.feature.costtracker.domain.model.CostPeriod
import com.hopcape.odo.feature.costtracker.presentation.CostTrackerTestTags
import com.hopcape.odo.feature.costtracker.presentation.state.Loadable
import com.hopcape.odo.feature.costtracker.resources.Res
import com.hopcape.odo.feature.costtracker.resources.ct_across
import com.hopcape.odo.feature.costtracker.resources.ct_avg_month
import com.hopcape.odo.feature.costtracker.resources.ct_avg_per_month
import com.hopcape.odo.feature.costtracker.resources.ct_cat_fuel
import com.hopcape.odo.feature.costtracker.resources.ct_cat_repairs
import com.hopcape.odo.feature.costtracker.resources.ct_cat_service
import com.hopcape.odo.feature.costtracker.resources.ct_cost_per_km_label
import com.hopcape.odo.feature.costtracker.resources.ct_cost_per_mi_label
import com.hopcape.odo.feature.costtracker.resources.ct_distance_driven
import com.hopcape.odo.feature.costtracker.resources.ct_fuel_note_city
import com.hopcape.odo.feature.costtracker.resources.ct_fuel_note_efficiency
import com.hopcape.odo.feature.costtracker.resources.ct_fuel_note_generic
import com.hopcape.odo.feature.costtracker.resources.ct_fuel_note_missing
import com.hopcape.odo.feature.costtracker.resources.ct_fuel_note_owner
import com.hopcape.odo.feature.costtracker.resources.ct_fuel_rate_action
import com.hopcape.odo.feature.costtracker.resources.ct_no_car_body
import com.hopcape.odo.feature.costtracker.resources.ct_no_car_title
import com.hopcape.odo.feature.costtracker.resources.ct_no_rate_title
import com.hopcape.odo.feature.costtracker.resources.ct_odometer_showcase
import com.hopcape.odo.feature.costtracker.resources.ct_showcase_dismiss
import com.hopcape.odo.feature.costtracker.resources.ct_per_unit_rate
import com.hopcape.odo.feature.costtracker.resources.ct_per_unit_suffix
import com.hopcape.odo.feature.costtracker.resources.ct_period_1y
import com.hopcape.odo.feature.costtracker.resources.ct_period_3m
import com.hopcape.odo.feature.costtracker.resources.ct_period_6m
import com.hopcape.odo.feature.costtracker.resources.ct_spend_by_month
import com.hopcape.odo.feature.costtracker.resources.ct_summary
import com.hopcape.odo.feature.costtracker.resources.ct_title
import com.hopcape.odo.feature.costtracker.resources.ct_total_spent
import com.hopcape.odo.feature.costtracker.resources.ct_unit_kg
import com.hopcape.odo.feature.costtracker.resources.ct_unit_kwh
import com.hopcape.odo.feature.costtracker.resources.ct_unit_litre
import com.hopcape.odo.feature.costtracker.resources.ct_where_it_goes
import org.jetbrains.compose.resources.stringResource

private val BarShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
private val MONTH_BAR_HEIGHT = 88.dp

/**
 * The "Running cost" screen — the per-km cost tracker. Leads with the headline cost/km +
 * trend, a period selector, a spend-by-month bar chart, the "where it goes" category
 * breakdown (each with its per-km share) and a summary.
 *
 * State-free: renders [state] and forwards events. The period chips stay live while the
 * figures under them are read, so switching windows never blanks the control the owner
 * just touched.
 */
@Composable
internal fun RunningCostScreen(
    state: RunningCostUiState,
    onEvent: (RunningCostEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(modifier = modifier, title = stringResource(Res.string.ct_title)) { padding ->
        if (state.noCar) {
            // No read is in flight, so a spinner here would spin forever; say what is
            // actually missing and where to fix it.
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                OdoEmptyState(
                    title = stringResource(Res.string.ct_no_car_title),
                    message = stringResource(Res.string.ct_no_car_body),
                    icon = {
                        OdoIcon(
                            IcCar,
                            contentDescription = null,
                            tint = OdoTheme.colors.textMuted,
                            size = OdoTheme.iconSizes.large,
                        )
                    },
                )
            }
            return@OdoScreen
        }
        val heroAnchor = rememberCoachMarkAnchorState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            val content = (state.content as? Loadable.Ready)?.value
            CostHeroCard(
                content = content,
                failure = state.content as? Loadable.Failed,
                modifier = Modifier.coachMarkAnchor(heroAnchor),
            )
            // The headline above is free — it is the figure Home's tile already shows.
            // Everything from here down is the analysis Pro sells (#247).
            if (state.analysisLocked) {
                if (content != null) LockedAnalysis(content) { onEvent(RunningCostEvent.UnlockAnalysisTapped) }
            } else {
                PeriodSelector(
                    selected = state.period,
                    onChange = { onEvent(RunningCostEvent.PeriodSelected(it)) },
                )
                if (content != null) {
                    SpendByMonthCard(bars = content.spendBars, avgPerMonth = content.avgPerMonth)
                    SectionLabel(stringResource(Res.string.ct_where_it_goes))
                    CategoryCard(
                        categories = content.categories,
                        fuelNote = content.fuelNote,
                        fuelEfficiencyUnit = state.fuelEfficiencyUnit,
                        onEditRate = { onEvent(RunningCostEvent.FuelRateTapped) },
                    )
                    SectionLabel(stringResource(Res.string.ct_summary))
                    SummaryCard(content)
                }
            }
        }

        // The odometer coach mark (#229): fires on the disappointment, so its copy
        // explains why the figure is empty rather than congratulating a discovery.
        if (state.odometerShowcase) {
            OdoCoachMark(
                text = stringResource(Res.string.ct_odometer_showcase),
                dismissLabel = stringResource(Res.string.ct_showcase_dismiss),
                anchor = heroAnchor,
                onDismiss = { onEvent(RunningCostEvent.OdometerShowcaseDismissed) },
            )
        }
    }
}

/**
 * The headline. A car that has not been driven far enough gets the reason instead of a
 * number: a rate taken off forty kilometres is arithmetic, not information.
 */
@Composable
private fun CostHeroCard(
    content: RunningCostContent?,
    failure: Loadable.Failed?,
    modifier: Modifier = Modifier,
) {
    val distance = LocalOdoDistanceFormat.current
    OdoCard(modifier = modifier) {
        if (failure != null) {
            OdoText(failure.message.asString(), style = OdoTheme.typography.heading)
            return@OdoCard
        }
        OdoText(
            stringResource(distance.perDistanceLabel()),
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textMuted,
        )
        when (val headline = content?.headline) {
            // Still reading: the card keeps its shape rather than flashing a zero.
            null -> Unit

            is CostHeadline.NotEnoughYet -> {
                OdoText(stringResource(Res.string.ct_no_rate_title), style = OdoTheme.typography.heading)
                OdoText(
                    headline.message.asString(),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }

            is CostHeadline.Rate -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                        OdoText(
                            headline.perKm.perDistanceUnit(distance).formatRupeesDecimal(),
                            style = OdoTheme.typography.display,
                            modifier = Modifier.alignByBaseline().testTag(CostTrackerTestTags.COST_PER_KM),
                        )
                        OdoText(
                            stringResource(Res.string.ct_per_unit_suffix, distance.suffix),
                            style = OdoTheme.typography.heading,
                            color = OdoTheme.colors.textDim,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                    // No badge without a window before this one to compare against.
                    headline.trendPercent?.let { TrendBadge(percent = it, up = headline.trendUp) }
                }
                OdoText(
                    stringResource(
                        Res.string.ct_across,
                        distance.format(content.distance.km),
                        content.periodRange.asString(),
                    ),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
        }
    }
}

@Composable
private fun TrendBadge(percent: Int, up: Boolean) {
    OdoBadge(
        modifier = Modifier.testTag(CostTrackerTestTags.TREND_BADGE),
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

/**
 * What the analysis looks like without Pro (#247): the owner's own real chart and
 * categories behind a fade, with what Pro adds said plainly on top.
 *
 * Their own data rather than a mock-up, the same choice the health-score breakdown makes —
 * a lock over someone else's numbers is an advert, a lock over your own is an offer. The
 * ₹/km headline above stays readable throughout, so nothing that was on screen yesterday
 * has been taken away.
 */
@Composable
private fun LockedAnalysis(content: RunningCostContent, onUnlock: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(LockedAnalysisHeight).testTag(CostTrackerTestTags.PAYWALL)) {
        Column(
            Modifier.fillMaxWidth().alpha(0.18f),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            SpendByMonthCard(bars = content.spendBars, avgPerMonth = content.avgPerMonth)
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(OdoTheme.colors.bg.copy(alpha = 0.5f), OdoTheme.colors.bg)),
            ),
        )
        Column(
            modifier = Modifier.matchParentSize().padding(horizontal = OdoTheme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md, Alignment.CenterVertically),
        ) {
            Box(
                Modifier.size(LockedIconSize).clip(OdoTheme.shapes.card)
                    .background(OdoTheme.colors.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                OdoIcon(IcLockFilled, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.large)
            }
            OdoText(stringResource(Res.string.ct_locked_title), style = OdoTheme.typography.heading)
            OdoText(
                stringResource(Res.string.ct_locked_body),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
                textAlign = TextAlign.Center,
            )
            OdoButton(stringResource(Res.string.ct_locked_cta), onClick = onUnlock)
        }
    }
}

private val LockedAnalysisHeight = 320.dp
private val LockedIconSize = 56.dp

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
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoText(stringResource(Res.string.ct_spend_by_month), style = OdoTheme.typography.heading)
            OdoText(
                stringResource(Res.string.ct_avg_per_month, avgPerMonth.formatRupees()),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
        val max = bars.maxOfOrNull { it.amount.paise }?.coerceAtLeast(1) ?: 1
        Row(
            Modifier.fillMaxWidth().padding(top = OdoTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        ) {
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
        // The 1Y period packs two-month labels ("Aug–Oct") under sixth-width columns;
        // caption's tracked caps overflow there, so the axis drops the tracking and
        // pins to one line instead of wrapping mid-word.
        OdoText(
            bar.label.asString(),
            style = OdoTheme.typography.caption.copy(fontSize = 10.sp, letterSpacing = 0.sp),
            color = OdoTheme.colors.textDim,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun CategoryCard(
    categories: List<CostCategoryRow>,
    fuelNote: FuelNote,
    fuelEfficiencyUnit: FuelEfficiencyUnit,
    onEditRate: () -> Unit,
) {
    OdoCard(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
        val max = categories.maxOfOrNull { it.amount.paise }?.coerceAtLeast(1) ?: 1
        categories.forEach { row -> CategoryRow(row = row, max = max) }
        // Fuel is estimated, never logged, so the screen always says what it was estimated
        // from — including when there was nothing to estimate with — and always offers the
        // owner the correction, since the estimate is only as good as its price.
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            OdoText(
                fuelNoteText(fuelNote, fuelEfficiencyUnit),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
                modifier = Modifier.testTag(CostTrackerTestTags.FUEL_NOTE),
            )
            OdoButton(
                text = stringResource(Res.string.ct_fuel_rate_action),
                onClick = onEditRate,
                variant = OdoButtonVariant.Tertiary,
            )
        }
    }
}

@Composable
private fun CategoryRow(row: CostCategoryRow, max: Long) {
    val color = categoryColor(row.category)
    Column(
        modifier = Modifier.testTag(CostTrackerTestTags.categoryRow(row.category)),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            CategoryChip(color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(categoryLabel(row.category), style = OdoTheme.typography.heading)
                row.perKm?.let { perKm ->
                    OdoText(
                        stringResource(
                            Res.string.ct_per_unit_rate,
                            perKm.perDistanceUnit(LocalOdoDistanceFormat.current).formatRupeesDecimal(),
                            LocalOdoDistanceFormat.current.suffix,
                        ),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textDim,
                    )
                }
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
private fun SummaryCard(content: RunningCostContent) {
    OdoCard(
        contentPadding = PaddingValues(horizontal = OdoTheme.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        SummaryRow(stringResource(Res.string.ct_total_spent), content.totalSpent.formatRupees())
        HorizontalDivider(color = OdoTheme.colors.border)
        SummaryRow(
            stringResource(Res.string.ct_distance_driven),
            LocalOdoDistanceFormat.current.format(content.distance.km),
        )
        HorizontalDivider(color = OdoTheme.colors.border)
        SummaryRow(stringResource(Res.string.ct_avg_month), content.avgPerMonth.formatRupees())
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

/** The unit name is a resource of its own, so the note is composed here, not in the state. */
@Composable
private fun fuelNoteText(note: FuelNote, efficiencyUnit: FuelEfficiencyUnit): String = when (note) {
    FuelNote.Missing -> stringResource(Res.string.ct_fuel_note_missing)
    is FuelNote.Estimated -> {
        val price = note.pricePerUnit.formatRupeesDecimal()
        val unit = unitLabel(note.unit)
        val efficiency = efficiencyUnit.format(note.kmPerUnit, note.unit)
        val source = when {
            note.ownersOwn -> stringResource(Res.string.ct_fuel_note_owner, price, unit)
            note.city != null -> stringResource(Res.string.ct_fuel_note_city, price, unit, note.city)
            else -> stringResource(Res.string.ct_fuel_note_generic, price, unit)
        }
        // The assumed mileage is named, because it is the other half of a figure nobody
        // logged: a ₹/km built on 15 km/l is only as right as that 15.
        source + " " + stringResource(Res.string.ct_fuel_note_efficiency, efficiency)
    }
}

@Composable
private fun unitLabel(unit: FuelUnit): String = stringResource(
    when (unit) {
        FuelUnit.LITRE -> Res.string.ct_unit_litre
        FuelUnit.KILOGRAM -> Res.string.ct_unit_kg
        FuelUnit.KILOWATT_HOUR -> Res.string.ct_unit_kwh
    },
)

@Composable
private fun categoryColor(category: SpendCategory): Color = when (category) {
    SpendCategory.FUEL -> OdoTheme.colors.accent
    SpendCategory.SERVICE -> OdoTheme.colors.success
    SpendCategory.REPAIRS -> OdoTheme.colors.warning
}

@Composable
private fun categoryLabel(category: SpendCategory): String = stringResource(
    when (category) {
        SpendCategory.FUEL -> Res.string.ct_cat_fuel
        SpendCategory.SERVICE -> Res.string.ct_cat_service
        SpendCategory.REPAIRS -> Res.string.ct_cat_repairs
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
    RunningCostScreen(state = previewRunningCost(), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun RunningCostNotEnoughPreview() = OdoPreview(padded = false) {
    RunningCostScreen(state = previewRunningCostNoRate(), onEvent = {})
}

/**
 * "COST PER KILOMETRE" or "COST PER MILE" — the heading names the unit in full, so the
 * figure under it cannot be read as the other one.
 */
private fun OdoDistanceFormat.perDistanceLabel() = when (unit) {
    OdoDistanceUnit.KM -> Res.string.ct_cost_per_km_label
    OdoDistanceUnit.MILES -> Res.string.ct_cost_per_mi_label
}

/** A per-kilometre rate restated per shown unit. Money itself never changes, only the rate. */
private fun Amount.perDistanceUnit(format: OdoDistanceFormat): Amount =
    Amount.of(format.ratePaise(paise)).getOrElse { this }
