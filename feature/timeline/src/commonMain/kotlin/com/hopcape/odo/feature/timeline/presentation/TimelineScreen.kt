package com.hopcape.odo.feature.timeline.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButton
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCar
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcClock
import com.hopcape.odo.core.designsystem.icons.IcFilter
import com.hopcape.odo.core.designsystem.icons.IcJournal
import com.hopcape.odo.core.designsystem.icons.IcLightning
import com.hopcape.odo.core.designsystem.icons.IcShare
import com.hopcape.odo.core.designsystem.icons.IcShield
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.core.domain.shared.formatDayMonth
import com.hopcape.odo.core.domain.shared.formatKm
import com.hopcape.odo.core.domain.shared.formatMonthYear
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.feature.timeline.domain.model.ServiceTrust
import com.hopcape.odo.feature.timeline.domain.model.TimelineEvent
import com.hopcape.odo.feature.timeline.domain.model.trust
import com.hopcape.odo.feature.timeline.resources.Res
import com.hopcape.odo.feature.timeline.resources.tl_add_bill
import com.hopcape.odo.feature.timeline.resources.tl_badge_verified
import com.hopcape.odo.feature.timeline.resources.tl_cd_filter
import com.hopcape.odo.feature.timeline.resources.tl_cd_share
import com.hopcape.odo.feature.timeline.resources.tl_doc_insurance
import com.hopcape.odo.feature.timeline.resources.tl_doc_loan
import com.hopcape.odo.feature.timeline.resources.tl_doc_other
import com.hopcape.odo.feature.timeline.resources.tl_doc_puc
import com.hopcape.odo.feature.timeline.resources.tl_doc_rc
import com.hopcape.odo.feature.timeline.resources.tl_doc_renewed
import com.hopcape.odo.feature.timeline.resources.tl_doc_renewed_valid_till
import com.hopcape.odo.feature.timeline.resources.tl_empty_action
import com.hopcape.odo.feature.timeline.resources.tl_empty_body
import com.hopcape.odo.feature.timeline.resources.tl_empty_title
import com.hopcape.odo.feature.timeline.resources.tl_entry_meta
import com.hopcape.odo.feature.timeline.resources.tl_flagged_over
import com.hopcape.odo.feature.timeline.resources.tl_health_fell
import com.hopcape.odo.feature.timeline.resources.tl_health_rose
import com.hopcape.odo.feature.timeline.resources.tl_milestone_car_added
import com.hopcape.odo.feature.timeline.resources.tl_milestone_car_added_sub
import com.hopcape.odo.feature.timeline.resources.tl_self_reported
import com.hopcape.odo.feature.timeline.resources.tl_subtitle
import com.hopcape.odo.feature.timeline.resources.tl_subtitle_new
import com.hopcape.odo.feature.timeline.resources.tl_title
import org.jetbrains.compose.resources.stringResource

private val RailCellWidth = 56.dp
private val NodeSize = 40.dp

/**
 * The Timeline tab — the car's unified activity feed (services · documents · health-score
 * changes · milestones) on a vertical rail, grouped by month. State-free: it renders
 * [state] and forwards intents. A service card opens the shared `ServiceLog.Detail`;
 * the header actions open the filter sheet + share. Uses the Odo theme (no mockup teal).
 */
@Composable
internal fun TimelineScreen(
    state: TimelineUiState,
    onFilter: () -> Unit,
    onShare: () -> Unit,
    onOpenService: (ServiceLogId) -> Unit,
    onAddBill: () -> Unit,
    onScanFirst: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.tl_title),
        actions = {
            OdoCircularIconButton(IcFilter, contentDescription = stringResource(Res.string.tl_cd_filter), onClick = onFilter)
            OdoCircularIconButton(IcShare, contentDescription = stringResource(Res.string.tl_cd_share), onClick = onShare)
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                OdoLoadingIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(vertical = OdoTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            ) {
                OdoText(state.subtitle(), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
                TimelineFeed(
                    events = state.events,
                    showMonths = !state.isNewUser,
                    onOpenService = onOpenService,
                    onAddBill = onAddBill,
                )
                if (state.isNewUser) EmptyCta(onScanFirst)
            }
        }
    }
}

/** "Swift VXI · 14 events since 2020", or the softer line while the feed is still bare. */
@Composable
private fun TimelineUiState.subtitle(): String {
    val since = sinceYear
    return if (isNewUser || since == null) {
        stringResource(Res.string.tl_subtitle_new, carName)
    } else {
        stringResource(Res.string.tl_subtitle, carName, events.size, since)
    }
}

@Composable
private fun TimelineFeed(
    events: List<TimelineEvent>,
    showMonths: Boolean,
    onOpenService: (ServiceLogId) -> Unit,
    onAddBill: () -> Unit,
) {
    // Month sections are pure display grouping — the state carries a flat, newest-first
    // feed, and groupBy preserves that order for both the headers and the rows.
    val sections = remember(events) { events.groupBy { formatMonthYear(it.date).uppercase() } }
    val railColor = OdoTheme.colors.border
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        sections.forEach { (month, monthEvents) ->
            if (showMonths) {
                OdoText(
                    month,
                    style = OdoTheme.typography.caption,
                    color = OdoTheme.colors.textDim,
                    modifier = Modifier.padding(top = OdoTheme.spacing.sm),
                )
            }
            Column(
                Modifier.fillMaxWidth().drawBehind {
                    val x = RailCellWidth.toPx() / 2f
                    drawLine(railColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                },
            ) {
                monthEvents.forEach { event -> TimelineRow(event, onOpenService, onAddBill) }
            }
        }
    }
}

@Composable
private fun TimelineRow(
    event: TimelineEvent,
    onOpenService: (ServiceLogId) -> Unit,
    onAddBill: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(bottom = OdoTheme.spacing.lg)) {
        Box(Modifier.width(RailCellWidth), contentAlignment = Alignment.TopCenter) {
            NodeTile(event)
        }
        Box(Modifier.weight(1f)) {
            when (event) {
                is TimelineEvent.Service -> ServiceCard(event, onOpenService, onAddBill)
                is TimelineEvent.DocumentRenewed -> NoteRow(documentText(event), formatDayMonth(event.date))
                is TimelineEvent.HealthScoreChanged -> NoteRow(healthText(event), formatDayMonth(event.date))
                is TimelineEvent.CarAdded -> MilestoneCard(event)
            }
        }
    }
}

@Composable
private fun NodeTile(event: TimelineEvent) {
    val (icon, tint) = nodeInfo(event)
    // Opaque base covers the rail line running behind the node; the tint wash sits on top.
    Box(
        modifier = Modifier.size(NodeSize).clip(OdoTheme.shapes.field).background(OdoTheme.colors.bg),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().background(tint.copy(alpha = 0.15f)))
        OdoIcon(icon, contentDescription = null, tint = tint, size = OdoTheme.iconSizes.medium)
    }
}

@Composable
private fun ServiceCard(
    event: TimelineEvent.Service,
    onOpen: (ServiceLogId) -> Unit,
    onAddBill: () -> Unit,
) {
    val trust = event.trust
    OdoCard(
        onClick = { onOpen(event.id) },
        border = BorderStroke(
            1.dp,
            if (trust is ServiceTrust.Flagged) {
                OdoTheme.colors.warning.copy(alpha = 0.5f)
            } else {
                OdoTheme.colors.border
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OdoText(event.workDone, style = OdoTheme.typography.heading, maxLines = 1)
                val odometer = event.odometer.formatKm()
                OdoText(
                    event.workshop?.let { stringResource(Res.string.tl_entry_meta, it.value, odometer) } ?: odometer,
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                    maxLines = 1,
                )
            }
            OdoText(event.amount.formatRupees(), style = OdoTheme.typography.heading, maxLines = 1)
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TrustLabel(trust, onAddBill)
            Spacer(Modifier.weight(1f))
            OdoText(
                formatDayMonth(event.date),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun TrustLabel(trust: ServiceTrust, onAddBill: () -> Unit) {
    when (trust) {
        ServiceTrust.Verified -> OdoBadge(
            stringResource(Res.string.tl_badge_verified),
            tone = OdoBadgeTone.Success,
            leadingIcon = { OdoIcon(IcCheck, contentDescription = null, size = OdoTheme.iconSizes.small) },
        )

        is ServiceTrust.Flagged -> Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoIcon(IcWarning, contentDescription = null, tint = OdoTheme.colors.warning, size = OdoTheme.iconSizes.small)
            OdoText(
                stringResource(Res.string.tl_flagged_over, trust.overchargedBy.formatRupees()),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.warning,
            )
        }

        ServiceTrust.SelfReported -> Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoText(stringResource(Res.string.tl_self_reported), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            OdoText(
                stringResource(Res.string.tl_add_bill),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.accent,
                modifier = Modifier.clickable(onClick = onAddBill),
            )
        }
    }
}

/** An inline (non-card) event: a document renewal or a health-score move. */
@Composable
private fun NoteRow(text: String, date: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = OdoTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        OdoText(text, style = OdoTheme.typography.body, modifier = Modifier.weight(1f))
        OdoText(date, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textMuted)
    }
}

@Composable
private fun MilestoneCard(event: TimelineEvent.CarAdded) {
    OdoCard {
        OdoText(
            stringResource(Res.string.tl_milestone_car_added, event.carName),
            style = OdoTheme.typography.heading,
            maxLines = 1,
        )
        OdoText(
            stringResource(Res.string.tl_milestone_car_added_sub, formatDate(event.date)),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
    }
}

@Composable
private fun EmptyCta(onScanFirst: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = OdoTheme.spacing.xxl, bottom = OdoTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        Box(
            modifier = Modifier.size(88.dp).clip(OdoTheme.shapes.field).background(OdoTheme.colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(IcClock, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.large)
        }
        OdoText(stringResource(Res.string.tl_empty_title), style = OdoTheme.typography.title, textAlign = TextAlign.Center)
        OdoText(stringResource(Res.string.tl_empty_body), style = OdoTheme.typography.body, color = OdoTheme.colors.textDim, textAlign = TextAlign.Center)
        OdoButton(stringResource(Res.string.tl_empty_action), onClick = onScanFirst, modifier = Modifier.fillMaxWidth())
    }
}

/** "PUC renewed · valid till 30 Nov 2026". */
@Composable
private fun documentText(event: TimelineEvent.DocumentRenewed): String {
    val name = stringResource(
        when (event.document) {
            DocumentType.INSURANCE -> Res.string.tl_doc_insurance
            DocumentType.PUC -> Res.string.tl_doc_puc
            DocumentType.RC -> Res.string.tl_doc_rc
            DocumentType.LOAN -> Res.string.tl_doc_loan
            DocumentType.OTHER -> Res.string.tl_doc_other
        },
    )
    return event.validTill
        ?.let { stringResource(Res.string.tl_doc_renewed_valid_till, name, formatDate(it)) }
        ?: stringResource(Res.string.tl_doc_renewed, name)
}

/** "Health Score rose 70 → 74". */
@Composable
private fun healthText(event: TimelineEvent.HealthScoreChanged): String {
    val from = event.from.value
    val to = event.to.value
    val template = if (to >= from) Res.string.tl_health_rose else Res.string.tl_health_fell
    return stringResource(template, from, to)
}

@Composable
private fun nodeInfo(event: TimelineEvent): Pair<ImageVector, Color> {
    val c = OdoTheme.colors
    return when (event) {
        is TimelineEvent.Service -> IcJournal to when (event.trust) {
            ServiceTrust.Verified -> c.success
            is ServiceTrust.Flagged -> c.warning
            ServiceTrust.SelfReported -> c.textMuted
        }
        is TimelineEvent.DocumentRenewed -> IcShield to c.success
        is TimelineEvent.HealthScoreChanged -> IcLightning to c.accent
        is TimelineEvent.CarAdded -> IcCar to c.accent
    }
}
