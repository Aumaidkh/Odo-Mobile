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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoEmptyState
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcInfo
import com.hopcape.odo.core.designsystem.icons.IcShieldCheck
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
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
import com.hopcape.odo.feature.fairnesscheck.resources.fc_item_thin
import com.hopcape.odo.feature.fairnesscheck.resources.fc_item_unlabelled
import com.hopcape.odo.feature.fairnesscheck.resources.fc_no_city_action
import com.hopcape.odo.feature.fairnesscheck.resources.fc_no_city_body
import com.hopcape.odo.feature.fairnesscheck.resources.fc_no_city_title
import com.hopcape.odo.feature.fairnesscheck.resources.fc_none_body
import com.hopcape.odo.feature.fairnesscheck.resources.fc_none_headline
import com.hopcape.odo.feature.fairnesscheck.resources.fc_none_label
import com.hopcape.odo.feature.fairnesscheck.resources.fc_over_amount
import com.hopcape.odo.feature.fairnesscheck.resources.fc_over_body
import com.hopcape.odo.feature.fairnesscheck.resources.fc_over_headline
import com.hopcape.odo.feature.fairnesscheck.resources.fc_over_label
import com.hopcape.odo.feature.fairnesscheck.resources.fc_report
import com.hopcape.odo.feature.fairnesscheck.resources.fc_retry
import com.hopcape.odo.feature.fairnesscheck.resources.fc_thin_body
import com.hopcape.odo.feature.fairnesscheck.resources.fc_thin_headline
import com.hopcape.odo.feature.fairnesscheck.resources.fc_thin_label
import com.hopcape.odo.feature.fairnesscheck.resources.fc_thin_range
import com.hopcape.odo.feature.fairnesscheck.resources.fc_title
import com.hopcape.odo.feature.fairnesscheck.resources.fc_your_bill
import org.jetbrains.compose.resources.stringResource

/**
 * The fairness report — the verdict after the domain analyzer has run.
 *
 * Four heroes, not two. An overcharge is amber and a fair price is green, but a check that
 * ran on three bills, and a check that found no city average at all, each get their own
 * neutral card saying exactly that. Both used to be drawn as the green one, which is the
 * false precision the PRD forbids: "we don't know" must never read as "this looks fair".
 *
 * Stateless — the route host owns navigation and the state.
 */
@Composable
internal fun FairnessReportScreen(
    state: FairnessUiState,
    onEvent: (FairnessEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier.testTag(FairnessTestTags.SCREEN),
        title = stringResource(Res.string.fc_title),
        onBack = { onEvent(FairnessEvent.DoneTapped) },
        bottomBar = { BottomBar(state.content, onEvent) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            when (val content = state.content) {
                FairnessUiState.Content.Loading -> Skeleton()
                FairnessUiState.Content.NoCity -> NoCityState()
                is FairnessUiState.Content.Failed -> FailedState(content)
                is FairnessUiState.Content.Report -> ReportContent(content)
            }
        }
    }
}

@Composable
private fun ReportContent(report: FairnessUiState.Content.Report) {
    HeroCard(report)
    // Two bars are a comparison. With nothing benchmarked there is nothing to compare, and
    // equal-height bars would read as "you paid exactly the city average".
    report.cityAverageTotal?.let { average -> ComparisonCard(report, average) }
    BreakdownCard(report.lines)
}

/* ------------------------------- hero ------------------------------- */

@Composable
private fun HeroCard(report: FairnessUiState.Content.Report) {
    when (val verdict = report.verdict) {
        is FairnessVerdictUiState.Over -> Hero(
            tag = FairnessTestTags.HERO_OVER,
            tone = OdoTheme.colors.warning,
            icon = IcWarning,
            label = stringResource(Res.string.fc_over_label),
            headline = stringResource(Res.string.fc_over_headline),
            highlight = stringResource(Res.string.fc_over_amount, verdict.by.formatRupees()),
            body = stringResource(
                Res.string.fc_over_body,
                report.city,
                report.cityAverageTotal?.formatRupees().orEmpty(),
                report.yourTotal.formatRupees(),
            ),
        )

        is FairnessVerdictUiState.Fair -> Hero(
            tag = FairnessTestTags.HERO_FAIR,
            tone = OdoTheme.colors.success,
            icon = IcShieldCheck,
            label = stringResource(Res.string.fc_fair_label),
            headline = stringResource(Res.string.fc_fair_headline),
            highlight = stringResource(Res.string.fc_fair_highlight),
            body = stringResource(Res.string.fc_fair_body, verdict.difference.formatRupees(), report.city),
        )

        is FairnessVerdictUiState.TooLittleData -> Hero(
            tag = FairnessTestTags.HERO_THIN,
            tone = OdoTheme.colors.textMuted,
            icon = IcInfo,
            label = stringResource(Res.string.fc_thin_label),
            headline = stringResource(Res.string.fc_thin_headline),
            highlight = null,
            body = stringResource(Res.string.fc_thin_body, verdict.sampleSize, report.city),
        ) {
            // What the middle of the city paid is the only figure a thin pool can support.
            verdict.range?.let { range ->
                OdoText(
                    stringResource(Res.string.fc_thin_range, range.low.formatRupees(), range.high.formatRupees()),
                    style = OdoTheme.typography.body,
                    color = OdoTheme.colors.textDim,
                    modifier = Modifier.testTag(FairnessTestTags.THIN_RANGE),
                )
            }
        }

        FairnessVerdictUiState.NoBenchmark -> Hero(
            tag = FairnessTestTags.HERO_NO_BENCHMARK,
            tone = OdoTheme.colors.textMuted,
            icon = IcInfo,
            label = stringResource(Res.string.fc_none_label),
            headline = stringResource(Res.string.fc_none_headline),
            highlight = null,
            body = stringResource(Res.string.fc_none_body, report.city),
        )
    }
}

/**
 * The hero card's one shape. [highlight] is appended to [headline] in the card's tone and is
 * absent wherever there is no figure worth colouring — which is every state with no verdict.
 */
@Composable
private fun Hero(
    tag: String,
    tone: Color,
    icon: ImageVector,
    label: String,
    headline: String,
    highlight: String?,
    body: String,
    extra: @Composable () -> Unit = {},
) {
    OdoCard(
        modifier = Modifier.testTag(tag),
        color = tone.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.45f)),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(OdoTheme.shapes.small).background(tone.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { OdoIcon(icon, contentDescription = null, tint = tone, size = OdoTheme.iconSizes.medium) }
            OdoText(label, style = OdoTheme.typography.label, color = tone)
        }
        OdoText(
            buildAnnotatedString {
                append(headline)
                if (highlight != null) withStyle(SpanStyle(color = tone)) { append(highlight) }
            },
            style = OdoTheme.typography.title,
        )
        OdoText(body, style = OdoTheme.typography.body, color = OdoTheme.colors.textDim)
        extra()
    }
}

/* --------------------------- comparison --------------------------- */

@Composable
private fun ComparisonCard(report: FairnessUiState.Content.Report, cityAverageTotal: Amount) {
    val tone = if (report.verdict is FairnessVerdictUiState.Over) OdoTheme.colors.warning else OdoTheme.colors.success
    OdoCard(
        modifier = Modifier.testTag(FairnessTestTags.COMPARISON),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        OdoText(
            stringResource(Res.string.fc_basis, report.sampleSize, report.city.uppercase()),
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textMuted,
            modifier = Modifier.testTag(FairnessTestTags.BASIS),
        )
        val max = maxOf(report.yourTotal.paise, cityAverageTotal.paise, 1L)
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            Bar(
                amount = report.yourTotal.formatRupees(),
                label = stringResource(Res.string.fc_your_bill),
                fraction = report.yourTotal.paise.toFloat() / max,
                color = tone,
                modifier = Modifier.weight(1f),
            )
            Bar(
                amount = cityAverageTotal.formatRupees(),
                label = stringResource(Res.string.fc_city_avg),
                fraction = cityAverageTotal.paise.toFloat() / max,
                color = OdoTheme.colors.surfaceRaised,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Bar(amount: String, label: String, fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
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

/* --------------------------- breakdown --------------------------- */

@Composable
private fun BreakdownCard(lines: List<FairnessLineUiState>) {
    OdoCard(
        modifier = Modifier.testTag(FairnessTestTags.BREAKDOWN),
        contentPadding = PaddingValues(horizontal = OdoTheme.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        lines.forEach { BreakdownRow(it) }
    }
}

@Composable
private fun BreakdownRow(line: FairnessLineUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(FairnessTestTags.BREAKDOWN_ROW)
            .padding(vertical = OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The label yields (weight + wrap) so the amounts on the right always get their
        // full intrinsic width — a long part name must never squeeze "Rs. 13,000" into
        // a one-character-wide column.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
        ) {
            OdoText(
                line.label ?: stringResource(Res.string.fc_item_unlabelled),
                style = OdoTheme.typography.heading,
                maxLines = 2,
            )
            OdoText(
                text = line.cityAverage?.let { stringResource(Res.string.fc_item_avg, it.formatRupees()) }
                    ?: stringResource(Res.string.fc_item_no_benchmark),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
                maxLines = 1,
            )
        }
        when (val verdict = line.verdict) {
            is FairnessLineVerdictUiState.Over ->
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
                ) {
                    OdoText(
                        line.paid.formatRupees(),
                        style = OdoTheme.typography.heading,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OdoIcon(IcWarning, contentDescription = null, tint = OdoTheme.colors.warning, size = OdoTheme.iconSizes.small)
                        OdoText(
                            stringResource(Res.string.fc_item_over, verdict.by.formatRupees()),
                            style = OdoTheme.typography.label,
                            color = OdoTheme.colors.warning,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }

            FairnessLineVerdictUiState.Fair ->
                OdoText(
                    stringResource(Res.string.fc_fair_verdict),
                    style = OdoTheme.typography.label,
                    color = OdoTheme.colors.success,
                    maxLines = 1,
                    softWrap = false,
                )

            // Judged on too thin a pool: the row says so instead of borrowing "Fair".
            FairnessLineVerdictUiState.TooLittleData ->
                OdoText(
                    stringResource(Res.string.fc_item_thin),
                    style = OdoTheme.typography.label,
                    color = OdoTheme.colors.textMuted,
                    maxLines = 1,
                    softWrap = false,
                )

            // Nothing to compare against: show what was paid, claim nothing.
            FairnessLineVerdictUiState.NoBenchmark ->
                OdoText(
                    line.paid.formatRupees(),
                    style = OdoTheme.typography.heading,
                    maxLines = 1,
                    softWrap = false,
                )
        }
    }
}

/* --------------------------- other states --------------------------- */

@Composable
private fun NoCityState() {
    OdoEmptyState(
        modifier = Modifier.testTag(FairnessTestTags.NO_CITY),
        title = stringResource(Res.string.fc_no_city_title),
        message = stringResource(Res.string.fc_no_city_body),
        icon = { OdoIcon(IcInfo, contentDescription = null, tint = OdoTheme.colors.textMuted, size = OdoTheme.iconSizes.large) },
    )
}

@Composable
private fun FailedState(content: FairnessUiState.Content.Failed) {
    OdoEmptyState(
        modifier = Modifier.testTag(FairnessTestTags.ERROR),
        title = content.message.asString(),
        icon = { OdoIcon(IcWarning, contentDescription = null, tint = OdoTheme.colors.warning, size = OdoTheme.iconSizes.large) },
    )
}

/**
 * The waiting state.
 *
 * Blocks in the shape the report will take, not a spinner: the check is a network read, and
 * the screen it resolves into should not jump under the owner's thumb.
 */
@Composable
private fun Skeleton() {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(FairnessTestTags.SKELETON),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        SkeletonBlock(height = 180.dp)
        SkeletonBlock(height = 200.dp)
        SkeletonBlock(height = 120.dp)
    }
}

@Composable
private fun SkeletonBlock(height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(OdoTheme.shapes.card)
            .background(OdoTheme.colors.surface),
    )
}

/* ----------------------------- actions ----------------------------- */

@Composable
private fun BottomBar(content: FairnessUiState.Content, onEvent: (FairnessEvent) -> Unit) {
    // Nothing to act on while the check is still running.
    if (content is FairnessUiState.Content.Loading) return

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
        when (content) {
            is FairnessUiState.Content.Report ->
                if (content.canReport) {
                    OdoButton(
                        stringResource(Res.string.fc_report),
                        onClick = { onEvent(FairnessEvent.ReportTapped) },
                        modifier = Modifier.fillMaxWidth().testTag(FairnessTestTags.REPORT_BUTTON),
                    )
                }

            FairnessUiState.Content.NoCity ->
                OdoButton(
                    stringResource(Res.string.fc_no_city_action),
                    onClick = { onEvent(FairnessEvent.SetCityTapped) },
                    modifier = Modifier.fillMaxWidth().testTag(FairnessTestTags.SET_CITY_BUTTON),
                )

            is FairnessUiState.Content.Failed ->
                OdoButton(
                    stringResource(Res.string.fc_retry),
                    onClick = { onEvent(FairnessEvent.RetryTapped) },
                    variant = OdoButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth().testTag(FairnessTestTags.RETRY_BUTTON),
                )

            FairnessUiState.Content.Loading -> Unit
        }
        OdoText(
            stringResource(Res.string.fc_done),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            modifier = Modifier
                .clip(OdoTheme.shapes.pill)
                .testTag(FairnessTestTags.DONE_BUTTON)
                .clickable { onEvent(FairnessEvent.DoneTapped) }
                .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.sm),
        )
    }
}

private val BAR_AREA = 120.dp

@OdoThemePreviews
@Composable
private fun FairnessOverPreview() = OdoPreview(padded = false) {
    FairnessReportScreen(state = FairnessUiState(sampleOverReport()), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun FairnessFairPreview() = OdoPreview(padded = false) {
    FairnessReportScreen(state = FairnessUiState(sampleFairReport()), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun FairnessTooLittleDataPreview() = OdoPreview(padded = false) {
    FairnessReportScreen(state = FairnessUiState(sampleThinReport()), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun FairnessNoBenchmarkPreview() = OdoPreview(padded = false) {
    FairnessReportScreen(state = FairnessUiState(sampleNoBenchmarkReport()), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun FairnessNoCityPreview() = OdoPreview(padded = false) {
    FairnessReportScreen(state = FairnessUiState(FairnessUiState.Content.NoCity), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun FairnessLoadingPreview() = OdoPreview(padded = false) {
    FairnessReportScreen(state = FairnessUiState(FairnessUiState.Content.Loading), onEvent = {})
}
