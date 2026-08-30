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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCalendar
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcChevronRight
import com.hopcape.odo.core.designsystem.icons.IcClock
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.icons.IcDropletFilled
import com.hopcape.odo.core.designsystem.icons.IcGearFilled
import com.hopcape.odo.core.designsystem.icons.IcLeafFilled
import com.hopcape.odo.core.designsystem.icons.IcPlusLarge
import com.hopcape.odo.core.designsystem.icons.IcShieldFilled
import com.hopcape.odo.core.designsystem.icons.IcTyre
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.reminder.model.ReminderPreset
import com.hopcape.odo.feature.reminders.presentation.state.Loadable
import com.hopcape.odo.feature.reminders.resources.Res
import com.hopcape.odo.feature.reminders.resources.rm_actions_reschedule
import com.hopcape.odo.feature.reminders.resources.rm_actions_snooze
import com.hopcape.odo.feature.reminders.resources.rm_actions_turn_off
import com.hopcape.odo.feature.reminders.resources.rm_cd_reschedule
import com.hopcape.odo.feature.reminders.resources.rm_cd_snooze
import com.hopcape.odo.feature.reminders.resources.rm_cd_turn_off
import com.hopcape.odo.feature.reminders.resources.rm_attention_body
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
 * "set up reminders" section: a mix of already-scheduled reminders further out and
 * not-yet-created suggestions, which is why it isn't called "Upcoming" — half its rows
 * aren't yet. A "Manage" affordance sits in the header and a FAB adds one.
 *
 * Every row in that section opens something on tap: an already-scheduled one opens its
 * actions sheet, a suggestion opens the create form pre-filled with that preset's
 * defaults (its "Remind me" button stays too, for accepting them outright).
 *
 * State-free: renders [state] and forwards intents. The tap callbacks carry the row's
 * *resolved* copy alongside the data, because only the composition can resolve a
 * resource and the sheet / one-tap create / suggestion prefill all need the string.
 */
@Composable
internal fun RemindersScreen(
    state: RemindersUiState,
    onManage: () -> Unit,
    onOpenActions: (ReminderRow, String, String) -> Unit,
    onRemindMe: (ReminderPreset, String) -> Unit,
    onOpenSuggestion: (ReminderPreset, String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        topBar = { RemindersTopBar(onManage) },
        floatingActionButton = { AddFab(onAdd) },
    ) { padding ->
        when (val content = state.content) {
            // The local DB answers in milliseconds; a spinner would only flash.
            Loadable.Loading -> Box(Modifier.fillMaxSize().padding(padding))

            is Loadable.Failed -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                OdoText(content.message.asString(), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            }

            is Loadable.Ready -> RemindersLoadedContent(content.value, padding, onOpenActions, onRemindMe, onOpenSuggestion)
        }
    }
}

@Composable
private fun RemindersLoadedContent(
    content: RemindersContent,
    padding: PaddingValues,
    onOpenActions: (ReminderRow, String, String) -> Unit,
    onRemindMe: (ReminderPreset, String) -> Unit,
    onOpenSuggestion: (ReminderPreset, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(vertical = OdoTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        HeaderCard(content.header)
        if (content.thisWeek.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                SectionLabel(stringResource(Res.string.rm_this_week))
                content.thisWeek.forEach { row -> ThisWeekCard(row, onOpen = onOpenActions) }
            }
        }
        if (content.upcoming.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                SectionLabel(stringResource(Res.string.rm_upcoming))
                UpcomingCard(content.upcoming, onOpenActions, onRemindMe, onOpenSuggestion)
            }
        }
    }
}

/**
 * The actions sheet **body** for a "this week" reminder — reschedule, snooze, or turn it
 * off. Shown as a bottom-sheet destination ([OdoDestination.Reminders.Actions]) from
 * tapping the reminder's card; the ModalBottomSheet chrome comes from the navigation
 * layer. The header echoes the tapped card; "turn off" uses the danger tone.
 *
 * [showReschedule] is false for derived reminders (their dates come from documents and
 * service history); [showSnooze] is false when there is no dated occurrence to dismiss
 * (a distance target).
 */
@Composable
internal fun ReminderActionsSheetContent(
    icon: ReminderIcon,
    title: String,
    due: String,
    showReschedule: Boolean,
    showSnooze: Boolean,
    onReschedule: () -> Unit,
    onSnooze: () -> Unit,
    onTurnOff: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.xl)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconChip(iconFor(icon), toneFor(icon))
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(title, style = OdoTheme.typography.heading)
                OdoText(due, style = OdoTheme.typography.label, color = OdoTheme.colors.warning)
            }
        }
        OdoCard(
            contentPadding = PaddingValues(horizontal = OdoTheme.spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (showReschedule) {
                ActionRow(IcCalendar, stringResource(Res.string.rm_actions_reschedule), stringResource(Res.string.rm_cd_reschedule), OdoTheme.colors.text, chevron = true, onClick = onReschedule)
                HorizontalDivider(color = OdoTheme.colors.border)
            }
            if (showSnooze) {
                ActionRow(IcClock, stringResource(Res.string.rm_actions_snooze), stringResource(Res.string.rm_cd_snooze), OdoTheme.colors.text, chevron = true, onClick = onSnooze)
                HorizontalDivider(color = OdoTheme.colors.border)
            }
            ActionRow(IcClose, stringResource(Res.string.rm_actions_turn_off), stringResource(Res.string.rm_cd_turn_off), OdoTheme.colors.danger, chevron = false, onClick = onTurnOff)
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    tint: Color,
    chevron: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIcon(icon, contentDescription = contentDescription, tint = tint, size = OdoTheme.iconSizes.medium)
        OdoText(label, style = OdoTheme.typography.heading, color = tint, modifier = Modifier.weight(1f))
        if (chevron) {
            OdoIcon(IcChevronRight, contentDescription = null, tint = OdoTheme.colors.textMuted, size = OdoTheme.iconSizes.small)
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
            OdoIcon(IcGearFilled, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.small)
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
        is RemindersHeader.Attention -> stringResource(Res.string.rm_attention_body)
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
private fun ThisWeekCard(row: ReminderRow, onOpen: (ReminderRow, String, String) -> Unit) {
    val title = row.title.asString()
    val due = row.line.asString()
    OdoCard(onClick = { onOpen(row, title, due) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconChip(iconFor(row.icon), toneFor(row.icon))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(title, style = OdoTheme.typography.heading)
                OdoText(due, style = OdoTheme.typography.label, color = OdoTheme.colors.warning)
            }
            OdoIcon(
                IcChevronRight,
                contentDescription = stringResource(Res.string.rm_cd_open),
                tint = OdoTheme.colors.textMuted,
                size = OdoTheme.iconSizes.small,
            )
        }
    }
}

@Composable
private fun UpcomingCard(
    rows: List<ReminderRow>,
    onOpenActions: (ReminderRow, String, String) -> Unit,
    onRemindMe: (ReminderPreset, String) -> Unit,
    onOpenSuggestion: (ReminderPreset, String) -> Unit,
) {
    OdoCard(
        contentPadding = PaddingValues(horizontal = OdoTheme.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = OdoTheme.colors.border)
            UpcomingRow(row, onOpenActions, onRemindMe, onOpenSuggestion)
        }
    }
}

/**
 * Every row opens something on tap. A row already on the schedule (on-track or due-soon)
 * opens the same actions sheet a "this week" card does — reschedule, snooze, turn off — so
 * an owner who wants to change it is not limited to waiting it out. A [RowStatus.Suggested]
 * row is not a reminder yet, so tapping it opens the create form pre-filled with that
 * preset's defaults instead — adjust before it exists, rather than create-then-reschedule.
 * Its own one-tap "Remind me" button stays too, for accepting the defaults outright.
 */
@Composable
private fun UpcomingRow(
    row: ReminderRow,
    onOpenActions: (ReminderRow, String, String) -> Unit,
    onRemindMe: (ReminderPreset, String) -> Unit,
    onOpenSuggestion: (ReminderPreset, String) -> Unit,
) {
    val title = row.title.asString()
    val due = row.line.asString()
    val status = row.status
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = {
                    if (status is RowStatus.Suggested) onOpenSuggestion(status.preset, title)
                    else onOpenActions(row, title, due)
                },
            )
            .padding(vertical = OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(iconFor(row.icon), toneFor(row.icon))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(title, style = OdoTheme.typography.heading)
            OdoText(due, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        }
        when (status) {
            RowStatus.DueThisWeek, RowStatus.DueSoon ->
                OdoBadge(stringResource(Res.string.rm_status_due_soon), tone = OdoBadgeTone.Warning)

            RowStatus.OnTrack -> OdoBadge(stringResource(Res.string.rm_status_on_track), tone = OdoBadgeTone.Success)

            is RowStatus.Suggested -> RemindMeButton(
                tag = RemindersTestTags.remindMe(status.preset.name),
                onClick = { onRemindMe(status.preset, title) },
            )
        }
    }
}

@Composable
private fun RemindMeButton(tag: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(OdoTheme.shapes.pill)
            .border(1.dp, OdoTheme.colors.border, OdoTheme.shapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.xs)
            .testTag(tag),
    ) {
        OdoText(stringResource(Res.string.rm_remind_me), style = OdoTheme.typography.label)
    }
}

@Composable
private fun AddFab(onAdd: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(OdoTheme.shapes.card)
            .background(OdoTheme.colors.accent)
            .clickable(onClick = onAdd)
            .testTag(RemindersTestTags.ADD_FAB),
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
    ReminderIcon.SHIELD -> IcShieldFilled
    ReminderIcon.OIL -> IcDropletFilled
    ReminderIcon.LEAF -> IcLeafFilled
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
    RemindersScreen(sampleRemindersAttention(), onManage = {}, onOpenActions = { _, _, _ -> }, onRemindMe = { _, _ -> }, onOpenSuggestion = { _, _ -> }, onAdd = {})
}

@OdoThemePreviews
@Composable
private fun RemindersCaughtUpPreview() = OdoPreview(padded = false) {
    RemindersScreen(sampleRemindersCaughtUp(), onManage = {}, onOpenActions = { _, _, _ -> }, onRemindMe = { _, _ -> }, onOpenSuggestion = { _, _ -> }, onAdd = {})
}
