package com.hopcape.odo.feature.billscanner.presentation.fairness

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcLock
import com.hopcape.odo.core.designsystem.icons.IcShieldCheck
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.feature.billscanner.resources.Res
import com.hopcape.odo.feature.billscanner.resources.bs_fairness_basis
import com.hopcape.odo.feature.billscanner.resources.bs_fairness_city_avg
import com.hopcape.odo.feature.billscanner.resources.bs_fairness_fair_body
import com.hopcape.odo.feature.billscanner.resources.bs_fairness_fair_headline
import com.hopcape.odo.feature.billscanner.resources.bs_fairness_fair_highlight
import com.hopcape.odo.feature.billscanner.resources.bs_fairness_fair_label
import com.hopcape.odo.feature.billscanner.resources.bs_fairness_fair_verdict
import com.hopcape.odo.feature.billscanner.resources.bs_fairness_free_scans
import com.hopcape.odo.feature.billscanner.resources.bs_fairness_go_pro
import com.hopcape.odo.feature.billscanner.resources.bs_fairness_item_avg
import com.hopcape.odo.feature.billscanner.resources.bs_fairness_item_over
import com.hopcape.odo.feature.billscanner.resources.bs_fairness_over_amount
import com.hopcape.odo.feature.billscanner.resources.bs_fairness_over_body
import com.hopcape.odo.feature.billscanner.resources.bs_fairness_over_headline
import com.hopcape.odo.feature.billscanner.resources.bs_fairness_over_label
import com.hopcape.odo.feature.billscanner.resources.bs_fairness_report
import com.hopcape.odo.feature.billscanner.resources.bs_fairness_save
import com.hopcape.odo.feature.billscanner.resources.bs_fairness_title
import com.hopcape.odo.feature.billscanner.resources.bs_fairness_your_bill
import org.jetbrains.compose.resources.stringResource

/**
 * The "Fairness check" screen — the verdict after a saved bill is benchmarked against
 * the city average. Two tones driven by [FairnessUiState.verdict]: an amber
 * "overcharge caught" hero (with a Report action) or a green "fair price" hero. Both
 * carry the your-bill-vs-city-avg comparison, the per-item breakdown, and the honest
 * sample-size basis line.
 *
 * State-free: renders [state] and forwards intents. The real benchmark (the
 * `get_fairness_estimate` RPC) + persistence land in M2.
 */
@Composable
internal fun FairnessScreen(
    state: FairnessUiState,
    onSave: () -> Unit,
    onReport: () -> Unit,
    onGoPro: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flagged = state.verdict == FairnessVerdict.OVER
    val tone = if (flagged) OdoTheme.colors.warning else OdoTheme.colors.success
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.bs_fairness_title),
        onBack = onBack,
        bottomBar = { FairnessBottomBar(state = state, onSave = onSave, onReport = onReport, onGoPro = onGoPro) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            HeroCard(state = state, tone = tone, flagged = flagged)
            ComparisonCard(state = state, tone = tone)
            BreakdownCard(state = state)
        }
    }
}

@Composable
private fun HeroCard(state: FairnessUiState, tone: Color, flagged: Boolean) {
    val prefix = stringResource(if (flagged) Res.string.bs_fairness_over_headline else Res.string.bs_fairness_fair_headline)
    val highlight = if (flagged) {
        stringResource(Res.string.bs_fairness_over_amount, state.difference.formatRupees())
    } else {
        stringResource(Res.string.bs_fairness_fair_highlight)
    }
    val body = if (flagged) {
        stringResource(
            Res.string.bs_fairness_over_body,
            state.city,
            state.cityAverage.formatRupees(),
            state.yourBill.formatRupees(),
        )
    } else {
        stringResource(Res.string.bs_fairness_fair_body, state.difference.formatRupees(), state.city)
    }
    OdoCard(
        color = tone.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.45f)),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(OdoTheme.shapes.small).background(tone.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                OdoIcon(IcShieldCheck, contentDescription = null, tint = tone, size = OdoTheme.iconSizes.medium)
            }
            OdoText(
                stringResource(if (flagged) Res.string.bs_fairness_over_label else Res.string.bs_fairness_fair_label),
                style = OdoTheme.typography.label,
                color = tone,
            )
        }
        OdoText(
            buildAnnotatedString {
                append(prefix)
                withStyle(SpanStyle(color = tone)) { append(highlight) }
            },
            style = OdoTheme.typography.title,
        )
        OdoText(body, style = OdoTheme.typography.body, color = OdoTheme.colors.textDim)
    }
}

@Composable
private fun ComparisonCard(state: FairnessUiState, tone: Color) {
    OdoCard(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
        OdoText(
            stringResource(Res.string.bs_fairness_basis, state.sampleSize, state.city.uppercase()),
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textMuted,
        )
        val max = maxOf(state.yourBill.paise, state.cityAverage.paise, 1L)
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            Bar(
                amount = state.yourBill.formatRupees(),
                label = stringResource(Res.string.bs_fairness_your_bill),
                fraction = state.yourBill.paise.toFloat() / max,
                color = tone,
                modifier = Modifier.weight(1f),
            )
            Bar(
                amount = state.cityAverage.formatRupees(),
                label = stringResource(Res.string.bs_fairness_city_avg),
                fraction = state.cityAverage.paise.toFloat() / max,
                color = OdoTheme.colors.surfaceRaised,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A single labelled comparison bar; its height is [fraction] of the tallest bar. */
@Composable
private fun Bar(amount: String, label: String, fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        OdoText(amount, style = OdoTheme.typography.heading)
        Box(Modifier.fillMaxWidth().height(BAR_AREA), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(BAR_AREA * fraction.coerceIn(0.06f, 1f))
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(color),
            )
        }
        OdoText(label, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
    }
}

@Composable
private fun BreakdownCard(state: FairnessUiState) {
    OdoCard(
        contentPadding = PaddingValues(horizontal = OdoTheme.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        state.lineItems.forEach { item ->
            BreakdownRow(item)
        }
    }
}

@Composable
private fun BreakdownRow(item: FairnessLineItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(item.label, style = OdoTheme.typography.heading)
            OdoText(
                stringResource(Res.string.bs_fairness_item_avg, item.cityAverage.formatRupees()),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
        if (item.over != null) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(item.amount.formatRupees(), style = OdoTheme.typography.heading)
                Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    OdoIcon(IcWarning, contentDescription = null, tint = OdoTheme.colors.warning, size = OdoTheme.iconSizes.small)
                    OdoText(
                        stringResource(Res.string.bs_fairness_item_over, item.over.formatRupees()),
                        style = OdoTheme.typography.label,
                        color = OdoTheme.colors.warning,
                    )
                }
            }
        } else {
            OdoText(stringResource(Res.string.bs_fairness_fair_verdict), style = OdoTheme.typography.label, color = OdoTheme.colors.success)
        }
    }
}

@Composable
private fun FairnessBottomBar(
    state: FairnessUiState,
    onSave: () -> Unit,
    onReport: () -> Unit,
    onGoPro: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OdoTheme.colors.bg)
            .navigationBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(top = OdoTheme.spacing.md, bottom = OdoTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        if (state.verdict == FairnessVerdict.OVER) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                OdoButton(stringResource(Res.string.bs_fairness_save), onClick = onSave, modifier = Modifier.weight(1f))
                OdoButton(stringResource(Res.string.bs_fairness_report), onClick = onReport, modifier = Modifier.weight(1f), variant = OdoButtonVariant.Secondary)
            }
        } else {
            OdoButton(stringResource(Res.string.bs_fairness_save), onClick = onSave, modifier = Modifier.fillMaxWidth())
        }
        FreeScansFooter(freeScans = 1, onGoPro = onGoPro)
    }
}

@Composable
private fun FreeScansFooter(freeScans: Int, onGoPro: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        OdoIcon(IcLock, contentDescription = null, tint = OdoTheme.colors.textMuted, size = OdoTheme.iconSizes.small)
        OdoText(stringResource(Res.string.bs_fairness_free_scans, freeScans), style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
        OdoText("·", style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
        OdoText(
            stringResource(Res.string.bs_fairness_go_pro),
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.accent,
            modifier = Modifier
                .clip(OdoTheme.shapes.pill)
                .clickable(onClick = onGoPro)
                .padding(horizontal = OdoTheme.spacing.xs, vertical = OdoTheme.spacing.xs),
        )
    }
}

private val BAR_AREA = 120.dp

@OdoThemePreviews
@Composable
private fun FairnessOverPreview() = OdoPreview(padded = false) {
    FairnessScreen(state = sampleFairnessOver(), onSave = {}, onReport = {}, onGoPro = {}, onBack = {})
}

@OdoThemePreviews
@Composable
private fun FairnessFairPreview() = OdoPreview(padded = false) {
    FairnessScreen(state = sampleFairnessFair(), onSave = {}, onReport = {}, onGoPro = {}, onBack = {})
}
