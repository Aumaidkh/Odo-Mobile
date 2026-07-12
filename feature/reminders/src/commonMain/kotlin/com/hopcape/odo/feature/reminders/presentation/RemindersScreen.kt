package com.hopcape.odo.feature.reminders.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcArrowLeft
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcClock
import com.hopcape.odo.core.designsystem.icons.IcDroplet
import com.hopcape.odo.core.designsystem.icons.IcGear
import com.hopcape.odo.core.designsystem.icons.IcLeaf
import com.hopcape.odo.core.designsystem.icons.IcPlusLarge
import com.hopcape.odo.core.designsystem.icons.IcShield
import com.hopcape.odo.core.designsystem.icons.IcTyre
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.reminders.resources.Res
import com.hopcape.odo.feature.reminders.resources.rm_attention_title_many
import com.hopcape.odo.feature.reminders.resources.rm_attention_title_one
import com.hopcape.odo.feature.reminders.resources.rm_caught_up_body
import com.hopcape.odo.feature.reminders.resources.rm_caught_up_title
import com.hopcape.odo.feature.reminders.resources.rm_cd_add
import com.hopcape.odo.feature.reminders.resources.rm_cd_open
import com.hopcape.odo.feature.reminders.resources.rm_manage
import com.hopcape.odo.feature.reminders.resources.rm_remind_me
import com.hopcape.odo.feature.reminders.resources.rm_status_due_soon
import com.hopcape.odo.feature.reminders.resources.rm_status_on_track
import com.hopcape.odo.feature.reminders.resources.rm_this_week
import com.hopcape.odo.feature.reminders.resources.rm_title
import com.hopcape.odo.feature.reminders.resources.rm_upcoming
import org.jetbrains.compose.resources.stringResource

/**
 * The Reminders home — a status-toned summary (amber "needs attention" / green "all
 * caught up"), an urgent "this week" section when relevant, and the always-present
 * "upcoming" forward look. A "Manage" affordance sits in the header and a FAB adds one.
 *
 * State-free: renders [state] and forwards intents. The reminder engine + schedules are M2.
 */
@Composable
internal fun RemindersScreen(
    state: RemindersUiState,
    onManage: () -> Unit,
    onOpen: (ThisWeekItem) -> Unit,
    onRemindMe: (UpcomingItem) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        topBar = { RemindersTopBar(onManage) },
        floatingActionButton = { AddFab(onAdd) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            HeaderCard(state.header)
            if (state.thisWeek.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                    SectionLabel(stringResource(Res.string.rm_this_week))
                    state.thisWeek.forEach { ThisWeekCard(it, onOpen) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                SectionLabel(stringResource(Res.string.rm_upcoming))
                UpcomingCard(state.upcoming, onRemindMe)
            }
        }
    }
}

@Composable
private fun RemindersTopBar(onManage: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoText(stringResource(Res.string.rm_title), style = OdoTheme.typography.title.copy(fontSize = 28.sp))
        Box(Modifier.weight(1f))
        Row(
            modifier = Modifier.clip(OdoTheme.shapes.pill).clickable(onClick = onManage).padding(horizontal = OdoTheme.spacing.sm, vertical = OdoTheme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoIcon(IcGear, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.small)
            OdoText(stringResource(Res.string.rm_manage), style = OdoTheme.typography.label, color = OdoTheme.colors.textDim)
        }
    }
}

@Composable
private fun HeaderCard(header: RemindersHeader) {
    val tone = if (header is RemindersHeader.Attention) OdoTheme.colors.warning else OdoTheme.colors.success
    val icon = if (header is RemindersHeader.Attention) IcClock else IcCheck
    val title = when (header) {
        is RemindersHeader.Attention -> stringResource(
            if (header.count == 1) Res.string.rm_attention_title_one else Res.string.rm_attention_title_many,
            header.count,
        )
        RemindersHeader.CaughtUp -> stringResource(Res.string.rm_caught_up_title)
    }
    val body = when (header) {
        is RemindersHeader.Attention -> header.detail
        RemindersHeader.CaughtUp -> stringResource(Res.string.rm_caught_up_body)
    }
    OdoCard(color = tone.copy(alpha = 0.10f), border = BorderStroke(1.dp, tone.copy(alpha = 0.45f))) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconChip(icon, tone)
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(title, style = OdoTheme.typography.heading)
                OdoText(body, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            }
        }
    }
}

@Composable
private fun ThisWeekCard(item: ThisWeekItem, onOpen: (ThisWeekItem) -> Unit) {
    OdoCard(onClick = { onOpen(item) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconChip(iconFor(item.icon), toneFor(item.icon))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(item.title, style = OdoTheme.typography.heading)
                OdoText(item.due, style = OdoTheme.typography.label, color = OdoTheme.colors.warning)
            }
            OdoIcon(
                IcArrowLeft,
                contentDescription = stringResource(Res.string.rm_cd_open),
                tint = OdoTheme.colors.textMuted,
                size = OdoTheme.iconSizes.small,
                modifier = Modifier.rotate(180f),
            )
        }
    }
}

@Composable
private fun UpcomingCard(items: List<UpcomingItem>, onRemindMe: (UpcomingItem) -> Unit) {
    OdoCard(
        contentPadding = PaddingValues(horizontal = OdoTheme.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = OdoTheme.colors.border)
            UpcomingRow(item, onRemindMe)
        }
    }
}

@Composable
private fun UpcomingRow(item: UpcomingItem, onRemindMe: (UpcomingItem) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(iconFor(item.icon), toneFor(item.icon))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(item.title, style = OdoTheme.typography.heading)
            OdoText(item.subtitle, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        }
        when (item.status) {
            UpcomingStatus.DueSoon -> OdoBadge(stringResource(Res.string.rm_status_due_soon), tone = OdoBadgeTone.Warning)
            UpcomingStatus.OnTrack -> OdoBadge(stringResource(Res.string.rm_status_on_track), tone = OdoBadgeTone.Success)
            UpcomingStatus.Suggested -> RemindMeButton { onRemindMe(item) }
        }
    }
}

@Composable
private fun RemindMeButton(onClick: () -> Unit) {
    Box(
        Modifier
            .clip(OdoTheme.shapes.pill)
            .border(1.dp, OdoTheme.colors.border, OdoTheme.shapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.xs),
    ) {
        OdoText(stringResource(Res.string.rm_remind_me), style = OdoTheme.typography.label)
    }
}

@Composable
private fun AddFab(onAdd: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(OdoTheme.shapes.card).background(OdoTheme.colors.accent).clickable(onClick = onAdd),
        contentAlignment = Alignment.Center,
    ) {
        OdoIcon(IcPlusLarge, contentDescription = stringResource(Res.string.rm_cd_add), tint = OdoTheme.colors.bg, size = OdoTheme.iconSizes.large)
    }
}

@Composable
private fun IconChip(icon: ImageVector, tone: Color) {
    Box(
        Modifier.size(44.dp).clip(OdoTheme.shapes.small).background(tone.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        OdoIcon(icon, contentDescription = null, tint = tone, size = OdoTheme.iconSizes.medium)
    }
}

@Composable
private fun SectionLabel(text: String) {
    OdoText(text, style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
}

private fun iconFor(icon: ReminderIcon): ImageVector = when (icon) {
    ReminderIcon.SHIELD -> IcShield
    ReminderIcon.OIL -> IcDroplet
    ReminderIcon.LEAF -> IcLeaf
    ReminderIcon.TYRE -> IcTyre
}

@Composable
private fun toneFor(icon: ReminderIcon): Color = when (icon) {
    ReminderIcon.SHIELD -> OdoTheme.colors.warning
    ReminderIcon.OIL -> OdoTheme.colors.accent
    ReminderIcon.LEAF -> OdoTheme.colors.success
    ReminderIcon.TYRE -> OdoTheme.colors.textMuted
}

@OdoThemePreviews
@Composable
private fun RemindersAttentionPreview() = OdoPreview(padded = false) {
    RemindersScreen(sampleRemindersAttention(), onManage = {}, onOpen = {}, onRemindMe = {}, onAdd = {})
}

@OdoThemePreviews
@Composable
private fun RemindersCaughtUpPreview() = OdoPreview(padded = false) {
    RemindersScreen(sampleRemindersCaughtUp(), onManage = {}, onOpen = {}, onRemindMe = {}, onAdd = {})
}
