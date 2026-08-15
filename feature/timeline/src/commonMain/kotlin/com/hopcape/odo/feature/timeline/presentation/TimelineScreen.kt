package com.hopcape.odo.feature.timeline.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButton
import com.hopcape.odo.core.designsystem.component.OdoEmptyState
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCar
import com.hopcape.odo.core.designsystem.icons.IcFuelPump
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcClock
import com.hopcape.odo.core.designsystem.icons.IcFilter
import com.hopcape.odo.core.designsystem.icons.IcJournal
import com.hopcape.odo.core.designsystem.icons.IcLightningFilled
import com.hopcape.odo.core.designsystem.icons.IcShare
import com.hopcape.odo.core.designsystem.icons.IcShieldFilled
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.core.domain.shared.formatDayMonth
import com.hopcape.odo.core.domain.shared.formatMonthYear
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.feature.timeline.domain.model.ServiceTrust
import com.hopcape.odo.feature.timeline.domain.model.trust
import com.hopcape.odo.feature.timeline.presentation.state.Loadable
import com.hopcape.odo.feature.timeline.resources.Res
import com.hopcape.odo.feature.timeline.resources.tl_add_bill
import com.hopcape.odo.feature.timeline.resources.tl_badge_verified
import com.hopcape.odo.feature.timeline.resources.tl_cd_filter
import com.hopcape.odo.feature.timeline.resources.tl_cd_share
import com.hopcape.odo.feature.timeline.resources.tl_empty_action
import com.hopcape.odo.feature.timeline.resources.tl_empty_body
import com.hopcape.odo.feature.timeline.resources.tl_empty_title
import com.hopcape.odo.feature.timeline.resources.tl_entry_meta
import com.hopcape.odo.feature.timeline.resources.tl_filtered_empty_body
import com.hopcape.odo.feature.timeline.resources.tl_filtered_empty_title
import com.hopcape.odo.feature.timeline.resources.tl_flagged_over
import com.hopcape.odo.feature.timeline.resources.tl_health_fell
import com.hopcape.odo.feature.timeline.resources.tl_health_rose
import com.hopcape.odo.feature.timeline.resources.tl_milestone_car_added
import com.hopcape.odo.feature.timeline.resources.tl_milestone_car_added_sub
import com.hopcape.odo.feature.timeline.resources.tl_no_car_body
import com.hopcape.odo.feature.timeline.resources.tl_no_car_title
import com.hopcape.odo.feature.timeline.resources.tl_self_reported
import com.hopcape.odo.feature.timeline.resources.tl_subtitle
import com.hopcape.odo.feature.timeline.resources.tl_subtitle_filtered
import com.hopcape.odo.feature.timeline.resources.tl_subtitle_new
import com.hopcape.odo.feature.timeline.resources.tl_title
import com.hopcape.odo.feature.timeline.ui.documentText
import com.hopcape.odo.feature.timeline.ui.fuelText
import com.hopcape.odo.feature.timeline.ui.workDoneText
import org.jetbrains.compose.resources.stringResource

private val RailCellWidth = 56.dp
private val NodeSize = 40.dp

/**
 * The Timeline tab — the car's unified activity feed (services · documents · health-score
 * moves · milestones) on a vertical rail, grouped by month. State-free: it renders [state]
 * and forwards intents. A service card opens the shared `ServiceLog.Detail`; the header
 * actions open the filter sheet and share.
 */
@Composable
internal fun TimelineScreen(
    state: TimelineUiState,
    onEvent: (TimelineEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.tl_title),
        actions = {
            OdoCircularIconButton(
                IcFilter,
                contentDescription = stringResource(Res.string.tl_cd_filter),
                onClick = { onEvent(TimelineEvent.FilterTapped) },
                modifier = Modifier.testTag(TimelineTestTags.FILTER_BUTTON),
            )
            OdoCircularIconButton(
                IcShare,
                contentDescription = stringResource(Res.string.tl_cd_share),
                onClick = { onEvent(TimelineEvent.ShareTapped) },
                modifier = Modifier.testTag(TimelineTestTags.SHARE_BUTTON),
            )
        },
    ) { padding ->
        if (state.noCar) {
            // No read is in flight, so a spinner here would spin forever; say what is
            // actually missing and where to fix it.
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                OdoEmptyState(
                    title = stringResource(Res.string.tl_no_car_title),
                    message = stringResource(Res.string.tl_no_car_body),
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
        when (val content = state.content) {
            Loadable.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                OdoLoadingIndicator()
            }

            is Loadable.Failed -> Box(
                Modifier.fillMaxSize().padding(padding).padding(OdoTheme.spacing.screenEdge),
                contentAlignment = Alignment.Center,
            ) {
                OdoText(
                    content.message.asString(),
                    style = OdoTheme.typography.body,
                    color = OdoTheme.colors.textDim,
                    textAlign = TextAlign.Center,
                )
            }

            is Loadable.Ready -> TimelineFeed(
                content = content.value,
                onEvent = onEvent,
                contentPadding = padding,
            )
        }
    }
}

/**
 * The feed itself. A `LazyColumn` rather than a scrolling `Column`: a five-year record is
 * hundreds of rows, and composing every one of them to show ten is what makes a tab feel
 * slow the longer someone uses the app.
 */
@Composable
private fun TimelineFeed(
    content: TimelineContent,
    onEvent: (TimelineEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    // Month sections are pure display grouping — the state carries a flat, newest-first
    // feed, and groupBy preserves that order for both the headers and the rows.
    val sections = remember(content.events) {
        content.events.groupBy { formatMonthYear(it.date).uppercase() }
    }
    val railColor = OdoTheme.colors.border
    val showMonths = !content.isNewUser

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(TimelineTestTags.FEED),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        item {
            OdoText(
                content.subtitle(),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
                modifier = Modifier
                    .padding(top = OdoTheme.spacing.md)
                    .testTag(TimelineTestTags.SUBTITLE),
            )
        }

        sections.forEach { (month, monthEvents) ->
            if (showMonths) {
                item(key = "month-$month") {
                    OdoText(
                        month,
                        style = OdoTheme.typography.caption,
                        color = OdoTheme.colors.textDim,
                        modifier = Modifier.padding(top = OdoTheme.spacing.sm),
                    )
                }
            }
            items(monthEvents, key = { it.rowKey }) { event ->
                Box(
                    Modifier.fillMaxWidth().drawBehind {
                        val x = RailCellWidth.toPx() / 2f
                        drawLine(railColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                    },
                ) {
                    TimelineRow(event, onEvent)
                }
            }
        }

        if (content.isNewUser) item { EmptyCta(onEvent) }
        if (content.isFilteredEmpty) item { FilteredEmpty() }
    }
}

/** Stable identity for a row, so scrolling and re-emissions don't rebuild the whole feed. */
private val ActivityEvent.rowKey: String
    get() = when (this) {
        is ActivityEvent.Service -> "service-${id.value}"
        is ActivityEvent.DocumentFiled -> "doc-${id.value}"
        is ActivityEvent.FuelFilled -> "fuel-${id.value}"
        is ActivityEvent.ScoreChanged -> "score-$date"
        is ActivityEvent.CarAdded -> "car-$date"
    }

/** "Swift VXI · 14 events since 2020", or what the filter has narrowed that to. */
@Composable
private fun TimelineContent.subtitle(): String {
    val since = sinceYear
    return when {
        isFiltered -> stringResource(Res.string.tl_subtitle_filtered, carName, events.size, totalEvents)
        isNewUser || since == null -> stringResource(Res.string.tl_subtitle_new, carName)
        else -> stringResource(Res.string.tl_subtitle, carName, events.size, since)
    }
}

@Composable
private fun TimelineRow(event: ActivityEvent, onEvent: (TimelineEvent) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = OdoTheme.spacing.lg)) {
        Box(Modifier.width(RailCellWidth), contentAlignment = Alignment.TopCenter) {
            NodeTile(event)
        }
        Box(Modifier.weight(1f)) {
            when (event) {
                is ActivityEvent.Service -> ServiceCard(event, onEvent)
                is ActivityEvent.DocumentFiled -> NoteRow(
                    text = documentText(event),
                    date = formatDayMonth(event.date),
                    modifier = Modifier.testTag(TimelineTestTags.documentRow(event.document.name)),
                )

                // A one-liner rather than a card. A fill is the most frequent thing on the
                // feed, and giving each one a card would bury the services between them.
                is ActivityEvent.FuelFilled -> NoteRow(
                    text = fuelText(event),
                    date = formatDayMonth(event.date),
                    modifier = Modifier.testTag(TimelineTestTags.FUEL_ROW),
                )

                is ActivityEvent.ScoreChanged -> NoteRow(
                    text = healthText(event),
                    date = formatDayMonth(event.date),
                    modifier = Modifier.testTag(TimelineTestTags.SCORE_ROW),
                )

                is ActivityEvent.CarAdded -> MilestoneCard(event)
            }
        }
    }
}

@Composable
private fun NodeTile(event: ActivityEvent) {
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
private fun ServiceCard(event: ActivityEvent.Service, onEvent: (TimelineEvent) -> Unit) {
    val trust = event.trust
    OdoCard(
        onClick = { onEvent(TimelineEvent.ServiceTapped(event.id)) },
        modifier = Modifier.testTag(TimelineTestTags.serviceRow(event.id.value)),
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
                OdoText(workDoneText(event.workDone), style = OdoTheme.typography.heading, maxLines = 1)
                val odometer = LocalOdoDistanceFormat.current.format(event.odometer.km)
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
            TrustLabel(trust, event, onEvent)
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
private fun TrustLabel(
    trust: ServiceTrust,
    event: ActivityEvent.Service,
    onEvent: (TimelineEvent) -> Unit,
) {
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
                modifier = Modifier
                    .testTag(TimelineTestTags.addBill(event.id.value))
                    .clickable { onEvent(TimelineEvent.AddBillTapped(event.id)) },
            )
        }
    }
}

/** An inline (non-card) event: a document filing or a health-score move. */
@Composable
private fun NoteRow(text: String, date: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = OdoTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        OdoText(text, style = OdoTheme.typography.body, modifier = Modifier.weight(1f))
        OdoText(date, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textMuted)
    }
}

@Composable
private fun MilestoneCard(event: ActivityEvent.CarAdded) {
    OdoCard(modifier = Modifier.testTag(TimelineTestTags.MILESTONE_ROW)) {
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
private fun EmptyCta(onEvent: (TimelineEvent) -> Unit) {
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
        OdoButton(
            stringResource(Res.string.tl_empty_action),
            onClick = { onEvent(TimelineEvent.ScanFirstTapped) },
            modifier = Modifier.fillMaxWidth().testTag(TimelineTestTags.EMPTY_CTA),
        )
    }
}

/** The feed is empty because of the filter, which is a different thing to say. */
@Composable
private fun FilteredEmpty() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = OdoTheme.spacing.xxl)
            .testTag(TimelineTestTags.FILTERED_EMPTY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        OdoText(stringResource(Res.string.tl_filtered_empty_title), style = OdoTheme.typography.title, textAlign = TextAlign.Center)
        OdoText(
            stringResource(Res.string.tl_filtered_empty_body),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            textAlign = TextAlign.Center,
        )
    }
}

/** "Health Score rose 70 → 74". */
@Composable
private fun healthText(event: ActivityEvent.ScoreChanged): String {
    val from = event.from.value
    val to = event.to.value
    val template = if (to >= from) Res.string.tl_health_rose else Res.string.tl_health_fell
    return stringResource(template, from, to)
}

@Composable
private fun nodeInfo(event: ActivityEvent): Pair<ImageVector, Color> {
    val c = OdoTheme.colors
    return when (event) {
        is ActivityEvent.Service -> IcJournal to when (event.trust) {
            ServiceTrust.Verified -> c.success
            is ServiceTrust.Flagged -> c.warning
            ServiceTrust.SelfReported -> c.textMuted
        }
        is ActivityEvent.DocumentFiled -> IcShieldFilled to c.success
        is ActivityEvent.FuelFilled -> IcFuelPump to c.textDim
        is ActivityEvent.ScoreChanged -> IcLightningFilled to c.accent
        is ActivityEvent.CarAdded -> IcCar to c.accent
    }
}
