package com.hopcape.odo.feature.servicelog.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.servicelog.presentation.ServiceLogTestTags
import com.hopcape.odo.feature.servicelog.presentation.list.components.FairnessTone
import com.hopcape.odo.feature.servicelog.presentation.list.components.TimelineStatus
import com.hopcape.odo.feature.servicelog.presentation.list.components.isFlagged
import com.hopcape.odo.feature.servicelog.presentation.list.components.tone
import com.hopcape.odo.feature.servicelog.presentation.ui.components.CardFooter
import com.hopcape.odo.feature.servicelog.presentation.ui.components.ServiceLogEntryCard
import com.hopcape.odo.feature.servicelog.presentation.ui.components.asString
import com.hopcape.odo.core.domain.shared.formatKm
import com.hopcape.odo.core.domain.shared.formatMonthYear
import com.hopcape.odo.core.domain.shared.formatRupees

/** Shown where a card has neither a workshop nor a described job. */
private const val EMPTY_FIELD = "—"

// Timeline rail geometry.
private val RailWidth = 28.dp
private val LineWidth = 2.dp
private val ConnectorTop = 18.dp
private val DotSize = 20.dp
private val DotIconSize = 12.dp

/**
 * Timeline (1b) list body — resale-proof first. Sits under the shared header: each
 * service is a card on a connecting rail whose dot reflects the fairness verdict
 * (green check = fair, amber = overcharged, hollow = unverified).
 */
@Composable
internal fun TimelineList(
    content: ServiceLogListUiState.Content.Loaded,
    onEvent: (ServiceLogListEvent) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = ServiceLogListBottomPadding)) {
        itemsIndexed(content.cards, key = { _, card -> card.id.value }) { index, card ->
            TimelineItem(
                card = card,
                isFirst = index == 0,
                isLast = index == content.cards.lastIndex,
                onClick = { onEvent(ServiceLogListEvent.Open.Entry(card.id)) },
                modifier = Modifier.animateItem().testTag(ServiceLogTestTags.card(card.id.value)),
            )
        }
    }
}

@Composable
private fun TimelineItem(
    card: ServiceLogCardUiState,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.height(IntrinsicSize.Min)) {
        TimelineRail(tone = card.fairness.tone(), isFirst = isFirst, isLast = isLast)
        Spacer(Modifier.width(OdoTheme.spacing.sm))
        RecordCard(card = card, onClick = onClick, modifier = Modifier.padding(vertical = OdoTheme.spacing.xs).weight(1f))
    }
}

@Composable
private fun TimelineRail(tone: FairnessTone, isFirst: Boolean, isLast: Boolean) {
    val line = OdoTheme.colors.border
    Column(modifier = Modifier.width(RailWidth).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(LineWidth).height(ConnectorTop).background(if (isFirst) Color.Transparent else line))
        TimelineDot(tone)
        Box(Modifier.width(LineWidth).weight(1f).background(if (isLast) Color.Transparent else line))
    }
}

@Composable
private fun TimelineDot(tone: FairnessTone) {
    when (tone) {
        FairnessTone.GOOD -> FilledDot(OdoTheme.colors.success, IcCheck)
        FairnessTone.WARN -> FilledDot(OdoTheme.colors.warning, IcWarning)
        FairnessTone.MUTED -> Box(Modifier.size(DotSize).clip(CircleShape).border(LineWidth, OdoTheme.colors.border, CircleShape))
    }
}

@Composable
private fun FilledDot(color: Color, icon: ImageVector) {
    Box(Modifier.size(DotSize).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        OdoIcon(icon, contentDescription = null, tint = OdoTheme.colors.onAccent, size = DotIconSize)
    }
}

/** A single service on the record: km + month pill, workshop·work, status + amount. */
@Composable
private fun RecordCard(card: ServiceLogCardUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ServiceLogEntryCard(onClick = onClick, flagged = card.isFlagged, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoText(text = card.odometer.formatKm(), style = OdoTheme.typography.title, modifier = Modifier.weight(1f))
            MonthPill(formatMonthYear(card.serviceDate))
        }
        OdoText(
            text = listOfNotNull(card.workshopName, card.workDone.asString())
                .ifEmpty { listOf(EMPTY_FIELD) }
                .joinToString(" · "),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
        )
        CardFooter(
            leading = { TimelineStatus(card.fairness) },
            trailing = { OdoText(text = card.amount.formatRupees(), style = OdoTheme.typography.title) },
        )
    }
}

@Composable
private fun MonthPill(text: String) {
    Surface(color = OdoTheme.colors.surfaceRaised, shape = OdoTheme.shapes.pill) {
        OdoText(
            text = text,
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textDim,
            modifier = Modifier.padding(horizontal = OdoTheme.spacing.sm, vertical = OdoTheme.spacing.xs),
        )
    }
}

@OdoThemePreviews
@Composable
private fun TimelineListPreview() = OdoPreview {
    TimelineList(content = sampleLoadedContent(), onEvent = {})
}
