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
import arrow.core.getOrElse
import kotlin.math.abs
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoIconButton
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.component.OdoThumbnail
import com.hopcape.odo.core.designsystem.icons.IcArrowLeft
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcChevronRight
import com.hopcape.odo.core.designsystem.icons.IcFileFilled
import com.hopcape.odo.core.designsystem.icons.IcImage
import com.hopcape.odo.core.designsystem.icons.IcPdf
import com.hopcape.odo.core.designsystem.icons.IcShare
import com.hopcape.odo.core.designsystem.icons.IcShieldCheck
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.core.domain.fairness.model.FairnessOutcome
import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.core.platform.file.StoredFileKind
import com.hopcape.odo.core.platform.file.StoredFileKinds
import com.hopcape.odo.core.platform.file.rememberStoredImage
import com.hopcape.odo.feature.servicelog.presentation.ui.components.CardFooter
import com.hopcape.odo.feature.servicelog.presentation.ui.components.IconLabel
import com.hopcape.odo.feature.servicelog.presentation.ui.components.VerificationBadge
import com.hopcape.odo.feature.servicelog.presentation.ui.components.asString
import com.hopcape.odo.feature.servicelog.presentation.ui.components.categoryLabel
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_badge_pdf
import com.hopcape.odo.feature.servicelog.resources.sl_cd_open_bill
import com.hopcape.odo.feature.servicelog.resources.sl_detail_bill_attached
import com.hopcape.odo.feature.servicelog.resources.sl_detail_bill_tap_hint
import com.hopcape.odo.feature.servicelog.resources.sl_detail_city_avg
import com.hopcape.odo.feature.servicelog.resources.sl_detail_discount
import com.hopcape.odo.feature.servicelog.resources.sl_detail_extras
import com.hopcape.odo.feature.servicelog.resources.sl_detail_fair_headline
import com.hopcape.odo.feature.servicelog.resources.sl_detail_fairness_basis
import com.hopcape.odo.feature.servicelog.resources.sl_detail_fairness_label
import com.hopcape.odo.feature.servicelog.resources.sl_detail_over_headline
import com.hopcape.odo.feature.servicelog.resources.sl_detail_attach_bill
import com.hopcape.odo.feature.servicelog.resources.sl_detail_check_fairness
import com.hopcape.odo.feature.servicelog.resources.sl_detail_report
import com.hopcape.odo.feature.servicelog.resources.sl_detail_reported
import com.hopcape.odo.feature.servicelog.resources.sl_detail_resale_subtitle
import com.hopcape.odo.feature.servicelog.resources.sl_detail_resale_title
import com.hopcape.odo.feature.servicelog.resources.sl_detail_share
import com.hopcape.odo.feature.servicelog.resources.sl_detail_thin_basis
import com.hopcape.odo.feature.servicelog.resources.sl_detail_thin_headline
import com.hopcape.odo.feature.servicelog.resources.sl_detail_thin_range
import com.hopcape.odo.feature.servicelog.resources.sl_detail_title
import com.hopcape.odo.feature.servicelog.resources.sl_detail_total_paid
import com.hopcape.odo.feature.servicelog.resources.sl_cd_back
import com.hopcape.odo.feature.servicelog.resources.sl_cd_share
import com.hopcape.odo.feature.servicelog.resources.sl_not_found
import com.hopcape.odo.feature.servicelog.resources.sl_verdict_fair
import com.hopcape.odo.feature.servicelog.resources.sl_verdict_low_confidence
import com.hopcape.odo.feature.servicelog.resources.sl_verdict_over
import org.jetbrains.compose.resources.stringResource

/** Shown where a line has neither a label nor a category to name it. */
private const val EMPTY_FIELD = "—"

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
    onEvent: (ServiceLogDetailEvent) -> Unit,
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
                    OdoIconButton(
                        IcArrowLeft,
                        contentDescription = stringResource(Res.string.sl_cd_back),
                        onClick = { onEvent(ServiceLogDetailEvent.BackClicked) },
                    )
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
            loaded?.let { DetailActions(it.entry, state, onEvent) }
        },
    ) { padding ->
        when (content) {
            ServiceLogDetailUiState.Content.Loading ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { OdoLoadingIndicator() }

            ServiceLogDetailUiState.Content.NotFound ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    OdoText(stringResource(Res.string.sl_not_found), style = OdoTheme.typography.body, color = OdoTheme.colors.textDim)
                }

            is ServiceLogDetailUiState.Content.Loaded -> DetailContent(content.entry, padding, onEvent)
        }
    }
}

@Composable
private fun DetailContent(
    entry: ServiceEntryDetailUiState,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onEvent: (ServiceLogDetailEvent) -> Unit,
) {
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
        entry.bill?.photoRef?.let { ref -> BillCard(photoRef = ref, onOpen = { onEvent(ServiceLogDetailEvent.BillTapped) }) }
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

/**
 * The bill behind the entry — a thumbnail, tappable, next to the line that says it is on file.
 *
 * Until now the photo was stored and never shown, which made "Verified" a claim the owner had
 * no way to check. A PDF has no image to draw here, so it falls back to a PDF glyph.
 */
@Composable
private fun BillCard(photoRef: String, onOpen: () -> Unit) {
    val isPdf = StoredFileKinds.of(photoRef) == StoredFileKind.PDF
    OdoCard(onClick = onOpen) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoThumbnail(
                image = rememberStoredImage(photoRef),
                contentDescription = stringResource(Res.string.sl_cd_open_bill),
                placeholderIcon = if (isPdf) IcPdf else IcImage,
                badge = if (isPdf) stringResource(Res.string.sl_badge_pdf) else null,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(stringResource(Res.string.sl_detail_bill_attached), style = OdoTheme.typography.heading)
                OdoText(
                    stringResource(Res.string.sl_detail_bill_tap_hint),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
            OdoIcon(
                IcChevronRight,
                contentDescription = null,
                tint = OdoTheme.colors.textMuted,
                size = OdoTheme.iconSizes.small,
            )
        }
    }
}

@Composable
private fun DetailHeader(entry: ServiceEntryDetailUiState) {
    // Workshop name lives in the collapsing top bar; the header carries the trust
    // badge + the "date · km · work" line.
    val distance = LocalOdoDistanceFormat.current
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
        VerificationBadge(entry.verification)
        OdoText(
            text = buildString {
                append(formatDate(entry.serviceDate))
                append(" · ").append(distance.format(entry.odometer.km))
                entry.workDone.asString()?.let { append(" · ").append(it) }
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

/**
 * The fairness headline + the evidence behind it. Amber over the average, green inside it,
 * and neutral when the pool is too thin to say either — a thin sample gets its own tone and
 * its own words, never the reassuring ones.
 */
@Composable
private fun FairnessCheckCard(fairness: EntryFairnessUiState.Assessed) {
    val outcome = fairness.overall
    val accent = when (outcome) {
        is FairnessOutcome.Over -> OdoTheme.colors.warning
        is FairnessOutcome.TooLittleData -> OdoTheme.colors.textMuted
        else -> OdoTheme.colors.success
    }
    OdoCard(
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
    ) {
        IconLabel(IcWarning, stringResource(Res.string.sl_detail_fairness_label).uppercase(), accent)
        OdoText(
            text = when (outcome) {
                is FairnessOutcome.Over -> stringResource(Res.string.sl_detail_over_headline, outcome.by.formatRupees())
                is FairnessOutcome.TooLittleData -> stringResource(Res.string.sl_detail_thin_headline)
                else -> stringResource(Res.string.sl_detail_fair_headline)
            },
            style = OdoTheme.typography.title,
        )
        OdoText(
            // The sample is the weakest line's, so the claim stays no stronger than its
            // thinnest comparison (PRD: never false precision).
            text = when (outcome) {
                is FairnessOutcome.TooLittleData ->
                    stringResource(Res.string.sl_detail_thin_basis, fairness.sampleSize, fairness.city)
                else -> stringResource(
                    Res.string.sl_detail_fairness_basis,
                    fairness.city,
                    fairness.cityAverageTotal.formatRupees(),
                    fairness.sampleSize,
                )
            },
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
        // What the middle of the city paid is the only honest figure a thin pool has.
        (outcome as? FairnessOutcome.TooLittleData)?.estimate?.range?.let { range ->
            OdoText(
                stringResource(Res.string.sl_detail_thin_range, range.low.formatRupees(), range.high.formatRupees()),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
    }
}

/** The per-line breakdown (label · city avg · paid · verdict) over the total. */
@Composable
private fun BreakdownCard(rows: List<FairnessBreakdownRow>, total: com.hopcape.odo.core.domain.shared.Amount) {
    OdoCard {
        rows.forEachIndexed { index, row ->
            if (index > 0) ItemDivider()
            BreakdownRow(row)
        }
        CardFooter(
            leading = { OdoText(stringResource(Res.string.sl_detail_total_paid), style = OdoTheme.typography.title) },
            trailing = { OdoText(total.formatRupees(), style = OdoTheme.typography.title) },
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
            OdoText(row.label ?: row.category?.let { categoryLabel(it) } ?: EMPTY_FIELD, style = OdoTheme.typography.body)
            row.cityAverage?.let {
                OdoText(
                    stringResource(Res.string.sl_detail_city_avg, it.formatRupees()),
                    style = OdoTheme.typography.caption,
                    color = OdoTheme.colors.textDim,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OdoText(row.paid.formatRupees(), style = OdoTheme.typography.body)
            // A line with no city benchmark gets no verdict at all — showing one would
            // invent a comparison that was never made.
            row.verdict?.let { VerdictLabel(it) }
        }
    }
}

@Composable
private fun VerdictLabel(verdict: FairnessVerdict) {
    when (verdict) {
        is FairnessVerdict.Over ->
            IconLabel(IcWarning, stringResource(Res.string.sl_verdict_over, verdict.by.formatRupees()), OdoTheme.colors.warning)
        is FairnessVerdict.Under ->
            IconLabel(IcCheck, stringResource(Res.string.sl_verdict_over, verdict.by.formatRupees()), OdoTheme.colors.success)
        FairnessVerdict.Fair ->
            IconLabel(IcCheck, stringResource(Res.string.sl_verdict_fair), OdoTheme.colors.success)
        // Too thin a sample to call: the line says so instead of borrowing "fair".
        is FairnessVerdict.LowConfidence ->
            OdoText(stringResource(Res.string.sl_verdict_low_confidence), style = OdoTheme.typography.label, color = OdoTheme.colors.textDim)
    }
}

/** Fallback for a self-reported entry with no fairness benchmark: plain line items. */
@Composable
private fun LineItemsCard(entry: ServiceEntryDetailUiState) {
    OdoCard {
        entry.lineItems.forEachIndexed { index, item ->
            if (index > 0) ItemDivider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                OdoText(item.label ?: categoryLabel(item.category), style = OdoTheme.typography.body, modifier = Modifier.weight(1f))
                OdoText(item.amount.formatRupees(), style = OdoTheme.typography.body)
            }
        }
        ExtrasRow(entry)
        CardFooter(
            leading = { OdoText(stringResource(Res.string.sl_detail_total_paid), style = OdoTheme.typography.title) },
            trailing = { OdoText(entry.totalPaid.formatRupees(), style = OdoTheme.typography.title) },
        )
    }
}

/**
 * Whatever the bill's total carries beyond its lines — tax, shop charges, or a discount.
 *
 * Derived, never read: no extractor produces a GST figure, and the parser drops CGST/SGST
 * rows before the lines are built. What is certain is the arithmetic — the printed total
 * minus what the lines add up to — so that is what is shown, under a label that does not
 * claim to know which of those it was. Without it the card visibly fails to add up, and an
 * owner checking Odo against their paper would conclude the app misread the bill.
 *
 * Drawn only when there are lines to add up and the two actually differ.
 */
@Composable
private fun ExtrasRow(entry: ServiceEntryDetailUiState) {
    if (entry.lineItems.isEmpty()) return
    val lines = entry.lineItems.sumOf { it.amount.paise }
    val difference = entry.totalPaid.paise - lines
    if (difference == 0L) return

    val label = if (difference > 0) Res.string.sl_detail_extras else Res.string.sl_detail_discount
    ItemDivider()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        OdoText(
            stringResource(label),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            modifier = Modifier.weight(1f),
        )
        OdoText(
            Amount.of(abs(difference)).getOrElse { Amount.ZERO }.formatRupees(),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
        )
    }
}

/** The hairline between bill lines — dimmer than [CardFooter]'s rule so the strong line
 *  stays reserved for the total, and the items merely read as separated. */
@Composable
private fun ItemDivider() {
    OdoDivider(color = OdoTheme.colors.border.copy(alpha = 0.6f))
}

/** Bottom-pinned actions: Share (verified) + Report (when the entry is over the average). */
@Composable
private fun DetailActions(
    entry: ServiceEntryDetailUiState,
    state: ServiceLogDetailUiState,
    onEvent: (ServiceLogDetailEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .systemBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        // A self-reported entry cannot be judged and cannot be shown to a buyer. Adding the
        // bill is the one action that changes both, so it leads.
        if (entry.verification != VerificationStatus.VERIFIED) {
            OdoButton(
                text = stringResource(Res.string.sl_detail_attach_bill),
                onClick = { onEvent(ServiceLogDetailEvent.AttachBillClicked) },
                modifier = Modifier.fillMaxWidth(),
                loading = state.attach.isInFlight,
                leadingIcon = { OdoIcon(IcFileFilled, contentDescription = null, size = OdoTheme.iconSizes.small) },
            )
        }
        state.attach.error?.let { message ->
            OdoText(
                message.asString(),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.danger,
            )
        }
        // Verified but never judged: the check is worth offering rather than waiting for the
        // next write to trigger it.
        if (entry.verification == VerificationStatus.VERIFIED && entry.fairness is EntryFairnessUiState.NotAssessed) {
            OdoButton(
                text = stringResource(Res.string.sl_detail_check_fairness),
                onClick = { onEvent(ServiceLogDetailEvent.CheckFairnessClicked) },
                modifier = Modifier.fillMaxWidth(),
                variant = OdoButtonVariant.Secondary,
            )
        }
        if (entry.resale is ResaleProofUiState.Verified) {
            OdoButton(
                text = stringResource(Res.string.sl_detail_share),
                onClick = { onEvent(ServiceLogDetailEvent.ShareClicked) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { OdoIcon(IcShare, contentDescription = null, size = OdoTheme.iconSizes.small) },
            )
        }
        if (entry.isOvercharged) {
            OdoButton(
                text = stringResource(if (state.reported) Res.string.sl_detail_reported else Res.string.sl_detail_report),
                onClick = { onEvent(ServiceLogDetailEvent.ReportOverchargeClicked) },
                modifier = Modifier.fillMaxWidth(),
                variant = OdoButtonVariant.Secondary,
                enabled = !state.reported,
            )
        }
    }
}
