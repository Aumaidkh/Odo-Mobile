package com.hopcape.odo.feature.fairnesscheck.presentation.report

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
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcShieldCheck
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.fairness.model.FairnessReportItem
import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.feature.fairnesscheck.resources.Res
import com.hopcape.odo.feature.fairnesscheck.resources.fc_basis
import com.hopcape.odo.feature.fairnesscheck.resources.fc_city_avg
import com.hopcape.odo.feature.fairnesscheck.resources.fc_done
import com.hopcape.odo.feature.fairnesscheck.resources.fc_fair_body
import com.hopcape.odo.feature.fairnesscheck.resources.fc_fair_headline
import com.hopcape.odo.feature.fairnesscheck.resources.fc_fair_highlight
import com.hopcape.odo.feature.fairnesscheck.resources.fc_fair_label
import com.hopcape.odo.feature.fairnesscheck.resources.fc_fair_verdict
import com.hopcape.odo.feature.fairnesscheck.resources.fc_item_avg
import com.hopcape.odo.feature.fairnesscheck.resources.fc_item_no_benchmark
import com.hopcape.odo.feature.fairnesscheck.resources.fc_item_over
import com.hopcape.odo.feature.fairnesscheck.resources.fc_item_unlabelled
import com.hopcape.odo.feature.fairnesscheck.resources.fc_over_amount
import com.hopcape.odo.feature.fairnesscheck.resources.fc_over_body
import com.hopcape.odo.feature.fairnesscheck.resources.fc_over_headline
import com.hopcape.odo.feature.fairnesscheck.resources.fc_over_label
import com.hopcape.odo.feature.fairnesscheck.resources.fc_report
import com.hopcape.odo.feature.fairnesscheck.resources.fc_title
import com.hopcape.odo.feature.fairnesscheck.resources.fc_your_bill
import org.jetbrains.compose.resources.stringResource

/**
 * The fairness report screen — the verdict after a [FairnessReport] is computed by the
 * domain analyzer. Two tones driven by [FairnessReport.overall]: an amber "overcharge
 * caught" hero (with a Report action) or a green "fair price" hero. Both carry the
 * your-bill-vs-city-avg comparison, the per-item breakdown, and the honest sample-size
 * basis line.
 *
 * Feature-agnostic: it renders a report and forwards generic intents (report / done), so
 * any caller reuses it via [OdoDestination.Fairness].
 */
@Composable
internal fun FairnessReportScreen(
    report: FairnessReport,
    onReport: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flagged = report.overall is FairnessVerdict.Over
    val tone = if (flagged) OdoTheme.colors.warning else OdoTheme.colors.success
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.fc_title),
        onBack = onBack,
        bottomBar = { BottomBar(flagged = flagged, onReport = onReport, onDone = onDone) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            HeroCard(report = report, tone = tone, flagged = flagged)
            ComparisonCard(report = report, tone = tone)
            BreakdownCard(report = report)
        }
    }
}

/** The amount the bill is off the city average — the highlighted figure in the hero. */
private fun FairnessReport.difference(): Amount = when (val v = overall) {
    is FairnessVerdict.Over -> v.by
    is FairnessVerdict.Under -> v.by
    else -> Amount.of(kotlin.math.abs(yourTotal.paise - cityAverageTotal.paise)).getOrNull() ?: Amount.ZERO
}

@Composable
private fun HeroCard(report: FairnessReport, tone: Color, flagged: Boolean) {
    val prefix = stringResource(if (flagged) Res.string.fc_over_headline else Res.string.fc_fair_headline)
    val highlight = if (flagged) {
        stringResource(Res.string.fc_over_amount, report.difference().formatRupees())
    } else {
        stringResource(Res.string.fc_fair_highlight)
    }
    val body = if (flagged) {
        stringResource(Res.string.fc_over_body, report.city, report.cityAverageTotal.formatRupees(), report.yourTotal.formatRupees())
    } else {
        stringResource(Res.string.fc_fair_body, report.difference().formatRupees(), report.city)
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
                stringResource(if (flagged) Res.string.fc_over_label else Res.string.fc_fair_label),
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
private fun ComparisonCard(report: FairnessReport, tone: Color) {
    OdoCard(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
        OdoText(
            stringResource(Res.string.fc_basis, report.sampleSize, report.city.uppercase()),
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textMuted,
        )
        val max = maxOf(report.yourTotal.paise, report.cityAverageTotal.paise, 1L)
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            Bar(report.yourTotal.formatRupees(), stringResource(Res.string.fc_your_bill), report.yourTotal.paise.toFloat() / max, tone, Modifier.weight(1f))
            Bar(report.cityAverageTotal.formatRupees(), stringResource(Res.string.fc_city_avg), report.cityAverageTotal.paise.toFloat() / max, OdoTheme.colors.surfaceRaised, Modifier.weight(1f))
        }
    }
}

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
private fun BreakdownCard(report: FairnessReport) {
    OdoCard(
        contentPadding = PaddingValues(horizontal = OdoTheme.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        report.items.forEach { BreakdownRow(it) }
    }
}

@Composable
private fun BreakdownRow(item: FairnessReportItem) {
    val over = (item.verdict as? FairnessVerdict.Over)?.by
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(item.label ?: stringResource(Res.string.fc_item_unlabelled), style = OdoTheme.typography.heading)
            val average = item.cityAverage
            OdoText(
                text = if (average != null) {
                    stringResource(Res.string.fc_item_avg, average.formatRupees())
                } else {
                    stringResource(Res.string.fc_item_no_benchmark)
                },
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
        when {
            over != null ->
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                    OdoText(item.amount.formatRupees(), style = OdoTheme.typography.heading)
                    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        OdoIcon(IcWarning, contentDescription = null, tint = OdoTheme.colors.warning, size = OdoTheme.iconSizes.small)
                        OdoText(stringResource(Res.string.fc_item_over, over.formatRupees()), style = OdoTheme.typography.label, color = OdoTheme.colors.warning)
                    }
                }
            // Judged and not over — the only case that may claim "Fair".
            item.verdict != null ->
                OdoText(stringResource(Res.string.fc_fair_verdict), style = OdoTheme.typography.label, color = OdoTheme.colors.success)
            // Nothing to compare against: show what was paid, claim nothing.
            else -> OdoText(item.amount.formatRupees(), style = OdoTheme.typography.heading)
        }
    }
}

@Composable
private fun BottomBar(flagged: Boolean, onReport: () -> Unit, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OdoTheme.colors.bg)
            .navigationBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(top = OdoTheme.spacing.md, bottom = OdoTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
    ) {
        if (flagged) {
            OdoButton(stringResource(Res.string.fc_report), onClick = onReport, modifier = Modifier.fillMaxWidth())
        }
        OdoText(
            stringResource(Res.string.fc_done),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            modifier = Modifier
                .clip(OdoTheme.shapes.pill)
                .clickable(onClick = onDone)
                .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.sm),
        )
    }
}

private val BAR_AREA = 120.dp

@OdoThemePreviews
@Composable
private fun FairnessReportOverPreview() = OdoPreview(padded = false) {
    FairnessReportScreen(report = sampleFairnessReport(over = true), onReport = {}, onDone = {}, onBack = {})
}

@OdoThemePreviews
@Composable
private fun FairnessReportFairPreview() = OdoPreview(padded = false) {
    FairnessReportScreen(report = sampleFairnessReport(over = false), onReport = {}, onDone = {}, onBack = {})
}
