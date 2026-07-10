package com.hopcape.odo.feature.servicelog.presentation.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGesturesPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoIconButton
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcArrowLeft
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcShare
import com.hopcape.odo.core.designsystem.icons.IcShieldCheck
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.feature.servicelog.presentation.formatDate
import com.hopcape.odo.feature.servicelog.presentation.formatKm
import com.hopcape.odo.feature.servicelog.presentation.formatRupees
import com.hopcape.odo.feature.servicelog.presentation.ui.components.CardFooter
import com.hopcape.odo.feature.servicelog.presentation.ui.components.IconLabel
import com.hopcape.odo.feature.servicelog.presentation.ui.components.VerificationBadge
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_detail_city_avg
import com.hopcape.odo.feature.servicelog.resources.sl_detail_fair_headline
import com.hopcape.odo.feature.servicelog.resources.sl_detail_fairness_basis
import com.hopcape.odo.feature.servicelog.resources.sl_detail_fairness_label
import com.hopcape.odo.feature.servicelog.resources.sl_detail_over_headline
import com.hopcape.odo.feature.servicelog.resources.sl_detail_report
import com.hopcape.odo.feature.servicelog.resources.sl_detail_reported
import com.hopcape.odo.feature.servicelog.resources.sl_detail_resale_subtitle
import com.hopcape.odo.feature.servicelog.resources.sl_detail_resale_title
import com.hopcape.odo.feature.servicelog.resources.sl_detail_share
import com.hopcape.odo.feature.servicelog.resources.sl_detail_title
import com.hopcape.odo.feature.servicelog.resources.sl_detail_total_paid
import com.hopcape.odo.feature.servicelog.resources.sl_cd_back
import com.hopcape.odo.feature.servicelog.resources.sl_cd_share
import com.hopcape.odo.feature.servicelog.resources.sl_not_found
import com.hopcape.odo.feature.servicelog.resources.sl_verdict_fair
import com.hopcape.odo.feature.servicelog.resources.sl_verdict_over
import org.jetbrains.compose.resources.stringResource

/**
 * A single service entry's detail — a combined "fairness + resale proof" view. Shows
 * the resale-proof card (for verified entries), the fairness check + a per-line
 * breakdown (when assessed), and pins Share / Report actions to the bottom bar.
 * Stateless: the route host owns navigation and the state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServiceLogDetailScreen(
    state: ServiceLogDetailUiState,
    onShare: () -> Unit,
    onReportOvercharge: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = state.content
    val loaded = content as? ServiceLogDetailUiState.Content.Loaded
    // The workshop name IS the title — large while at the top, collapsing into the bar
    // on scroll. Falls back to the generic screen title before the entry loads.
    val title = loaded?.entry?.workshopName ?: stringResource(Res.string.sl_detail_title)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = OdoTheme.colors.bg,
        topBar = {
            LargeTopAppBar(
                title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    OdoIconButton(IcArrowLeft, contentDescription = stringResource(Res.string.sl_cd_back), onClick = onBack)
                },
                // Tighten the expanded height (default 152dp) so the large title sits
                // just under the icon row instead of floating far below it.
                expandedHeight = 112.dp,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = OdoTheme.colors.bg,
                    scrolledContainerColor = OdoTheme.colors.bg,
                    titleContentColor = OdoTheme.colors.text,
                    navigationIconContentColor = OdoTheme.colors.text,
                    actionIconContentColor = OdoTheme.colors.text,
                ),
            )
        },
        bottomBar = {
            loaded?.let { DetailActions(it.entry, state.reported, onShare, onReportOvercharge) }
        },
    ) { padding ->
        when (content) {
            ServiceLogDetailUiState.Content.Loading ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { OdoLoadingIndicator() }

            ServiceLogDetailUiState.Content.NotFound ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    OdoText(stringResource(Res.string.sl_not_found), style = OdoTheme.typography.body, color = OdoTheme.colors.textDim)
                }

            is ServiceLogDetailUiState.Content.Loaded -> DetailContent(content.entry, padding)
        }
    }
}

@Composable
private fun DetailContent(entry: ServiceEntryDetailUiState, padding: androidx.compose.foundation.layout.PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        DetailHeader(entry)
        (entry.resale as? ResaleProofUiState.Verified)?.let { ResaleProofCard(it) }
        when (val fairness = entry.fairness) {
            is EntryFairnessUiState.Assessed -> {
                FairnessCheckCard(fairness)
                BreakdownCard(fairness.breakdown, entry.totalPaid)
            }
            EntryFairnessUiState.NotAssessed -> LineItemsCard(entry)
        }
    }
}

@Composable
private fun DetailHeader(entry: ServiceEntryDetailUiState) {
    // Workshop name lives in the collapsing top bar; the header carries the trust
    // badge + the "date · km · work" line.
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
        VerificationBadge(entry.verification)
        OdoText(
            text = buildString {
                append(formatDate(entry.serviceDate))
                append(" · ").append(formatKm(entry.odometer.km))
                entry.workDone?.let { append(" · ").append(it) }
            },
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
    }
}

/** The green "counts as resale proof" strip — the trust signal a buyer sees. */
@Composable
private fun ResaleProofCard(resale: ResaleProofUiState.Verified) {
    OdoCard(
        color = OdoTheme.colors.success.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, OdoTheme.colors.success.copy(alpha = 0.35f)),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(OdoTheme.colors.success.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                OdoIcon(IcShieldCheck, contentDescription = null, tint = OdoTheme.colors.success, size = OdoTheme.iconSizes.medium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(stringResource(Res.string.sl_detail_resale_title), style = OdoTheme.typography.heading)
                OdoText(
                    stringResource(Res.string.sl_detail_resale_subtitle, resale.scoreUplift),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
        }
    }
}

/** The fairness verdict headline + the estimate behind it. Amber when over the average. */
@Composable
private fun FairnessCheckCard(fairness: EntryFairnessUiState.Assessed) {
    val over = fairness.overall as? FairnessVerdict.Over
    val accent = if (over != null) OdoTheme.colors.warning else OdoTheme.colors.success
    OdoCard(
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
    ) {
        IconLabel(IcWarning, stringResource(Res.string.sl_detail_fairness_label).uppercase(), accent)
        OdoText(
            text = if (over != null) {
                stringResource(Res.string.sl_detail_over_headline, formatRupees(over.by.paise))
            } else {
                stringResource(Res.string.sl_detail_fair_headline)
            },
            style = OdoTheme.typography.title,
        )
        val estimate = fairness.estimate
        OdoText(
            text = stringResource(
                Res.string.sl_detail_fairness_basis,
                estimate.city,
                formatRupees(estimate.cityAverage.paise),
                estimate.sampleSize,
            ),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
    }
}

/** The per-line breakdown (label · city avg · paid · verdict) over the total. */
@Composable
private fun BreakdownCard(rows: List<FairnessBreakdownRow>, total: com.hopcape.odo.core.domain.shared.Amount) {
    OdoCard {
        rows.forEach { row -> BreakdownRow(row) }
        CardFooter(
            leading = { OdoText(stringResource(Res.string.sl_detail_total_paid), style = OdoTheme.typography.title) },
            trailing = { OdoText(formatRupees(total.paise), style = OdoTheme.typography.title) },
        )
    }
}

@Composable
private fun BreakdownRow(row: FairnessBreakdownRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OdoText(row.label, style = OdoTheme.typography.body)
            row.cityAverage?.let {
                OdoText(
                    stringResource(Res.string.sl_detail_city_avg, formatRupees(it.paise)),
                    style = OdoTheme.typography.caption,
                    color = OdoTheme.colors.textDim,
                )
            }
            row.note?.let { OdoText(it, style = OdoTheme.typography.caption, color = OdoTheme.colors.textDim) }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OdoText(formatRupees(row.paid.paise), style = OdoTheme.typography.body)
            VerdictLabel(row.verdict)
        }
    }
}

@Composable
private fun VerdictLabel(verdict: FairnessVerdict) {
    when (verdict) {
        is FairnessVerdict.Over ->
            IconLabel(IcWarning, stringResource(Res.string.sl_verdict_over, formatRupees(verdict.by.paise)), OdoTheme.colors.warning)
        is FairnessVerdict.Under ->
            IconLabel(IcCheck, stringResource(Res.string.sl_verdict_over, formatRupees(verdict.by.paise)), OdoTheme.colors.success)
        FairnessVerdict.Fair ->
            IconLabel(IcCheck, stringResource(Res.string.sl_verdict_fair), OdoTheme.colors.success)
        is FairnessVerdict.LowConfidence ->
            OdoText(stringResource(Res.string.sl_verdict_fair), style = OdoTheme.typography.label, color = OdoTheme.colors.textDim)
    }
}

/** Fallback for a self-reported entry with no fairness benchmark: plain line items. */
@Composable
private fun LineItemsCard(entry: ServiceEntryDetailUiState) {
    OdoCard {
        entry.lineItems.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                OdoText(item.label, style = OdoTheme.typography.body, modifier = Modifier.weight(1f))
                OdoText(formatRupees(item.amount.paise), style = OdoTheme.typography.body)
            }
        }
        CardFooter(
            leading = { OdoText(stringResource(Res.string.sl_detail_total_paid), style = OdoTheme.typography.title) },
            trailing = { OdoText(formatRupees(entry.totalPaid.paise), style = OdoTheme.typography.title) },
        )
    }
}

/** Bottom-pinned actions: Share (verified) + Report (when the entry is over the average). */
@Composable
private fun DetailActions(
    entry: ServiceEntryDetailUiState,
    reported: Boolean,
    onShare: () -> Unit,
    onReportOvercharge: () -> Unit,
) {
    val isOver = (entry.fairness as? EntryFairnessUiState.Assessed)?.overall is FairnessVerdict.Over
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .systemBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        if (entry.resale is ResaleProofUiState.Verified) {
            OdoButton(
                text = stringResource(Res.string.sl_detail_share),
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { OdoIcon(IcShare, contentDescription = null, size = OdoTheme.iconSizes.small) },
            )
        }
        if (isOver) {
            OdoButton(
                text = stringResource(if (reported) Res.string.sl_detail_reported else Res.string.sl_detail_report),
                onClick = onReportOvercharge,
                modifier = Modifier.fillMaxWidth(),
                variant = OdoButtonVariant.Secondary,
                enabled = !reported,
            )
        }
    }
}
