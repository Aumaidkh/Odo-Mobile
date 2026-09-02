package com.hopcape.odo.feature.challan.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcChevronDown
import com.hopcape.odo.core.designsystem.icons.IcMapPin
import com.hopcape.odo.core.designsystem.icons.IcShieldCheck
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.challan.presentation.ChallanTestTags
import com.hopcape.odo.feature.challan.presentation.state.Loadable
import com.hopcape.odo.feature.challan.resources.Res
import com.hopcape.odo.feature.challan.resources.ch_already_paid
import com.hopcape.odo.feature.challan.resources.ch_cd_back
import com.hopcape.odo.feature.challan.resources.ch_check_again
import com.hopcape.odo.feature.challan.resources.ch_check_on_parivahan
import com.hopcape.odo.feature.challan.resources.ch_clean_cleared_year
import com.hopcape.odo.feature.challan.resources.ch_clean_last_checked
import com.hopcape.odo.feature.challan.resources.ch_clean_next_check
import com.hopcape.odo.feature.challan.resources.ch_clean_title
import com.hopcape.odo.feature.challan.resources.ch_compliance_body
import com.hopcape.odo.feature.challan.resources.ch_compliance_title
import com.hopcape.odo.feature.challan.resources.ch_court_banner
import com.hopcape.odo.feature.challan.resources.ch_court_hearing_label
import com.hopcape.odo.feature.challan.resources.ch_court_label
import com.hopcape.odo.feature.challan.resources.ch_court_note
import com.hopcape.odo.feature.challan.resources.ch_down_body
import com.hopcape.odo.feature.challan.resources.ch_down_checked_ago
import com.hopcape.odo.feature.challan.resources.ch_down_last_known
import com.hopcape.odo.feature.challan.resources.ch_down_stale_note
import com.hopcape.odo.feature.challan.resources.ch_down_title
import com.hopcape.odo.feature.challan.resources.ch_payment_note
import com.hopcape.odo.feature.challan.resources.ch_refresh
import com.hopcape.odo.feature.challan.resources.ch_status_checked
import com.hopcape.odo.feature.challan.resources.ch_status_never_checked
import com.hopcape.odo.feature.challan.resources.ch_title
import com.hopcape.odo.feature.challan.resources.ch_total_pending_label
import com.hopcape.odo.feature.challan.resources.ch_try_again
import org.jetbrains.compose.resources.stringResource

/**
 * The owner's challans — one route, four faces: the pending list (with the court cases
 * pinned above and, past two years, the year sections + the collapsed "Older" bucket),
 * the clean state, the source-down state, and the plain load failure.
 *
 * Which face renders is decided entirely by [ChallanListUiState]; nothing here re-derives
 * a rule the ViewModel already owns.
 */
@Composable
internal fun ChallanListScreen(
    state: ChallanListUiState,
    onEvent: (ChallanListEvent) -> Unit,
) {
    OdoScreen(
        title = stringResource(Res.string.ch_title),
        onBack = { onEvent(ChallanListEvent.BackTapped) },
        backContentDescription = stringResource(Res.string.ch_cd_back),
        bottomBar = { BottomBar(state, onEvent) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).testTag(ChallanTestTags.LIST_SCREEN)) {
            when (val content = state.content) {
                Loadable.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = OdoTheme.colors.accent,
                )

                is Loadable.Failed -> OdoText(
                    content.message.asString(),
                    style = OdoTheme.typography.body,
                    color = OdoTheme.colors.textDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = OdoTheme.spacing.xl),
                )

                is Loadable.Ready -> when {
                    state.sourceDown -> SourceDownBody(content.value)
                    content.value.clean != null -> CleanBody(content.value, state, onEvent)
                    else -> PendingBody(content.value, state, onEvent)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Pending (mockups 3 · 4 · 6)

@Composable
private fun PendingBody(
    content: ChallanListContent,
    state: ChallanListUiState,
    onEvent: (ChallanListEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = OdoTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        StatusPill(content.checkedAgo, state.refreshing, onRefresh = { onEvent(ChallanListEvent.RefreshTapped) })

        if (content.courtCases.isNotEmpty()) {
            CourtBanner()
            content.courtCases.forEach { CourtCard(it) }
        }

        content.totalPending?.let { TotalPendingCard(it) }

        content.sections.forEach { section ->
            SectionHeader(section.title.asString())
            section.rows.forEach { row ->
                if (section.compact) CompactChallanCard(row) else ChallanCard(row)
            }
        }

        content.older?.let { older ->
            OlderRow(older, content.olderExpanded, onToggle = { onEvent(ChallanListEvent.OlderToggled) })
            if (content.olderExpanded) older.rows.forEach { CompactChallanCard(it) }
        }
    }
}

@Composable
private fun StatusPill(checkedAgo: UiText?, refreshing: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(OdoTheme.shapes.pill)
            .background(OdoTheme.colors.surfaceRaised)
            .clickable(enabled = !refreshing, onClick = onRefresh)
            .padding(horizontal = OdoTheme.spacing.lg, vertical = OdoTheme.spacing.sm)
            .testTag(ChallanTestTags.REFRESH_PILL),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (checkedAgo == null) OdoTheme.colors.warning else OdoTheme.colors.success),
        )
        OdoText(
            text = checkedAgo?.let { stringResource(Res.string.ch_status_checked, it.asString()) }
                ?: stringResource(Res.string.ch_status_never_checked),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (refreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = OdoTheme.colors.accent,
            )
        } else {
            OdoText(
                stringResource(Res.string.ch_refresh),
                style = OdoTheme.typography.label.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CourtBanner() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIcon(IcWarning, contentDescription = null, tint = OdoTheme.colors.danger, size = OdoTheme.iconSizes.small)
        OdoText(
            stringResource(Res.string.ch_court_banner).uppercase(),
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.danger,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun CourtCard(court: CourtCaseRow) {
    OdoCard(
        color = OdoTheme.colors.danger.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, OdoTheme.colors.danger.copy(alpha = 0.5f)),
        modifier = Modifier.testTag(ChallanTestTags.COURT_CARD),
    ) {
        RowTitle(court.violation, court.amount)
        ChallanNumber(court.number)
        OdoCard(
            color = OdoTheme.colors.surfaceRaised,
            border = null,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
        ) {
            LabelValueRow(stringResource(Res.string.ch_court_label), court.courtName.orEmpty(), bold = false)
            court.nextHearing?.let {
                LabelValueRow(stringResource(Res.string.ch_court_hearing_label), it, bold = true)
            }
        }
        OdoText(
            stringResource(Res.string.ch_court_note),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
    }
}

@Composable
private fun TotalPendingCard(card: TotalPendingCard) {
    OdoCard(modifier = Modifier.testTag(ChallanTestTags.TOTAL_CARD)) {
        OdoText(
            stringResource(Res.string.ch_total_pending_label).uppercase(),
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textMuted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.Bottom,
        ) {
            OdoText(card.amount, style = OdoTheme.typography.title, maxLines = 1)
            OdoText(
                card.countLine.asString(),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(bottom = OdoTheme.spacing.xs),
            )
        }
        if (card.segments.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clip(OdoTheme.shapes.pill),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                card.segments.forEachIndexed { index, segment ->
                    Box(
                        Modifier
                            .weight(segment.fraction.coerceAtLeast(0.05f))
                            .height(6.dp)
                            .background(
                                OdoTheme.colors.text.copy(alpha = 1f - index * 0.35f),
                            ),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
                card.segments.forEach { segment ->
                    OdoText(
                        "${segment.label} · ${segment.amount}",
                        style = OdoTheme.typography.caption,
                        color = OdoTheme.colors.textDim,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    OdoText(
        text.uppercase(),
        style = OdoTheme.typography.caption,
        color = OdoTheme.colors.textMuted,
        modifier = Modifier.padding(top = OdoTheme.spacing.sm),
    )
}

/** The full challan card — violation + number, then location and date (mockup 3 · 4). */
@Composable
private fun ChallanCard(row: ChallanRow) {
    OdoCard(modifier = Modifier.testTag(ChallanTestTags.challanCard(row.id))) {
        RowTitle(row.violation, row.amount)
        ChallanNumber(row.number)
        OdoDivider()
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            row.location?.let { location ->
                OdoIcon(IcMapPin, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.small)
                OdoText(
                    location,
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                    modifier = Modifier.weight(1f),
                )
            } ?: Spacer(Modifier.weight(1f))
            OdoText(row.date, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textMuted, maxLines = 1)
        }
    }
}

/** The compact card for year sections — date · number under the violation (mockup 6). */
@Composable
private fun CompactChallanCard(row: ChallanRow) {
    OdoCard(modifier = Modifier.testTag(ChallanTestTags.challanCard(row.id))) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(row.violation, style = OdoTheme.typography.heading)
                OdoText(
                    "${row.date} · ${row.number}",
                    style = OdoTheme.typography.caption,
                    color = OdoTheme.colors.textMuted,
                    maxLines = 1,
                )
            }
            OdoText(row.amount, style = OdoTheme.typography.heading, maxLines = 1)
        }
    }
}

@Composable
private fun OlderRow(older: OlderBucket, expanded: Boolean, onToggle: () -> Unit) {
    OdoCard(
        onClick = onToggle,
        border = BorderStroke(1.dp, OdoTheme.colors.textMuted.copy(alpha = 0.4f)),
        modifier = Modifier.testTag(ChallanTestTags.OLDER_ROW),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(older.countLine.asString(), style = OdoTheme.typography.heading)
                OdoText(
                    older.rangeLine.asString(),
                    style = OdoTheme.typography.caption,
                    color = OdoTheme.colors.textMuted,
                )
            }
            OdoText(older.amount, style = OdoTheme.typography.heading, maxLines = 1)
            OdoIcon(
                IcChevronDown,
                contentDescription = null,
                tint = OdoTheme.colors.textDim,
                size = OdoTheme.iconSizes.small,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Clean (mockup 5)

@Composable
private fun CleanBody(
    content: ChallanListContent,
    state: ChallanListUiState,
    onEvent: (ChallanListEvent) -> Unit,
) {
    val clean = content.clean ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = OdoTheme.spacing.md)
            .testTag(ChallanTestTags.CLEAN_STATE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(OdoTheme.colors.text),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(IcCheck, contentDescription = null, tint = OdoTheme.colors.bg, size = OdoTheme.iconSizes.large)
        }
        OdoText(
            stringResource(Res.string.ch_clean_title),
            style = OdoTheme.typography.title,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        OdoText(
            clean.body.asString(),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        OdoCard {
            clean.lastChecked?.let {
                LabelValueRow(stringResource(Res.string.ch_clean_last_checked), it.asString(), bold = true)
                OdoDivider()
            }
            clean.clearedThisYear?.let {
                LabelValueRow(stringResource(Res.string.ch_clean_cleared_year), it.asString(), bold = true)
                OdoDivider()
            }
            LabelValueRow(stringResource(Res.string.ch_clean_next_check), clean.nextCheck.asString(), bold = true)
        }
        OdoCard {
            Row(
                horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(OdoTheme.shapes.small)
                        .background(OdoTheme.colors.surfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    OdoIcon(IcShieldCheck, contentDescription = null, size = OdoTheme.iconSizes.medium)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                    OdoText(stringResource(Res.string.ch_compliance_title), style = OdoTheme.typography.heading)
                    OdoText(
                        stringResource(Res.string.ch_compliance_body),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textDim,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------------------------------
// Source down (mockup 10)

@Composable
private fun SourceDownBody(content: ChallanListContent) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = OdoTheme.spacing.md)
            .testTag(ChallanTestTags.SOURCE_DOWN),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        Spacer(Modifier.weight(1f))
        OdoCard(
            color = OdoTheme.colors.warning.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, OdoTheme.colors.warning.copy(alpha = 0.5f)),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(OdoTheme.shapes.small)
                        .background(OdoTheme.colors.warning.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    OdoIcon(IcWarning, contentDescription = null, tint = OdoTheme.colors.warning, size = OdoTheme.iconSizes.medium)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                    OdoText(stringResource(Res.string.ch_down_title), style = OdoTheme.typography.heading)
                    OdoText(
                        stringResource(Res.string.ch_down_body),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textDim,
                    )
                }
            }
        }
        content.checkedAgo?.let { age ->
            SectionHeader(stringResource(Res.string.ch_down_last_known))
            OdoCard(color = OdoTheme.colors.surfaceRaised, border = null) {
                OdoText(
                    stringResource(Res.string.ch_down_checked_ago, age.asString()).uppercase(),
                    style = OdoTheme.typography.caption,
                    color = OdoTheme.colors.textMuted,
                )
                content.totalPending?.let { total ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        OdoText(total.amount, style = OdoTheme.typography.title, maxLines = 1)
                        OdoText(
                            total.countLine.asString(),
                            style = OdoTheme.typography.bodySmall,
                            color = OdoTheme.colors.textDim,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(bottom = OdoTheme.spacing.xs),
                        )
                    }
                } ?: OdoText(
                    stringResource(Res.string.ch_clean_title),
                    style = OdoTheme.typography.heading,
                )
                OdoText(
                    stringResource(Res.string.ch_down_stale_note),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textMuted,
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------------------------------
// Bottom bar + shared bits

@Composable
private fun BottomBar(state: ChallanListUiState, onEvent: (ChallanListEvent) -> Unit) {
    val content = (state.content as? Loadable.Ready)?.value ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = OdoTheme.spacing.screenEdge,
                end = OdoTheme.spacing.screenEdge,
                top = OdoTheme.spacing.sm,
                bottom = OdoTheme.spacing.sm,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        when {
            state.sourceDown -> {
                OdoButton(
                    text = stringResource(Res.string.ch_try_again),
                    onClick = { onEvent(ChallanListEvent.TryAgainTapped) },
                    loading = state.refreshing,
                    modifier = Modifier.fillMaxWidth(),
                )
                OdoButton(
                    text = stringResource(Res.string.ch_check_on_parivahan),
                    onClick = { onEvent(ChallanListEvent.OpenParivahanTapped) },
                    variant = OdoButtonVariant.Tertiary,
                )
            }

            content.clean != null -> OdoButton(
                text = stringResource(Res.string.ch_check_again),
                onClick = { onEvent(ChallanListEvent.CheckAgainTapped) },
                variant = OdoButtonVariant.Secondary,
                loading = state.refreshing,
                modifier = Modifier.fillMaxWidth(),
            )

            content.pay != null -> {
                if (content.offerAlreadyPaid) PaymentNote()
                OdoButton(
                    text = content.pay.label.asString(),
                    onClick = { onEvent(ChallanListEvent.PayTapped) },
                    modifier = Modifier.fillMaxWidth().testTag(ChallanTestTags.PAY_BUTTON),
                )
                content.pay.caption?.let {
                    OdoText(
                        it.asString(),
                        style = OdoTheme.typography.caption,
                        color = OdoTheme.colors.textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (content.offerAlreadyPaid) {
                    OdoButton(
                        text = stringResource(Res.string.ch_already_paid),
                        onClick = { onEvent(ChallanListEvent.AlreadyPaidTapped) },
                        variant = OdoButtonVariant.Tertiary,
                        modifier = Modifier.testTag(ChallanTestTags.ALREADY_PAID),
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OdoTheme.shapes.field)
            .background(OdoTheme.colors.surface)
            .padding(horizontal = OdoTheme.spacing.lg, vertical = OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoText(
            stringResource(Res.string.ch_payment_note),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
    }
}

@Composable
private fun RowTitle(violation: String, amount: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoText(
            violation,
            style = OdoTheme.typography.heading,
            modifier = Modifier.weight(1f),
        )
        OdoText(amount, style = OdoTheme.typography.heading, maxLines = 1)
    }
}

@Composable
private fun ChallanNumber(number: String) {
    OdoText(number, style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted, maxLines = 1)
}

@Composable
private fun LabelValueRow(label: String, value: String, bold: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoText(label, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim, modifier = Modifier.weight(1f))
        OdoText(
            value,
            style = if (bold) OdoTheme.typography.label.copy(fontWeight = FontWeight.Bold) else OdoTheme.typography.label,
            maxLines = 1,
        )
    }
}
