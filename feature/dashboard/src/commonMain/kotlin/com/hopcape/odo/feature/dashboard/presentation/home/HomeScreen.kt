package com.hopcape.odo.feature.dashboard.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoHealthDial
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcBell
import com.hopcape.odo.core.designsystem.icons.IcCaretUp
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.core.domain.shared.formatRupeesDecimal
import com.hopcape.odo.feature.dashboard.resources.Res
import com.hopcape.odo.feature.dashboard.resources.hm_cd_bell
import com.hopcape.odo.feature.dashboard.resources.hm_cd_profile
import com.hopcape.odo.feature.dashboard.resources.hm_get_set_up
import com.hopcape.odo.feature.dashboard.resources.hm_greeting
import com.hopcape.odo.feature.dashboard.resources.hm_health_score
import com.hopcape.odo.feature.dashboard.resources.hm_per_km
import com.hopcape.odo.feature.dashboard.resources.hm_recent
import com.hopcape.odo.feature.dashboard.resources.hm_running_cost
import com.hopcape.odo.feature.dashboard.resources.hm_saved
import com.hopcape.odo.feature.dashboard.resources.hm_scan_first
import com.hopcape.odo.feature.dashboard.resources.hm_score_waiting
import com.hopcape.odo.feature.dashboard.resources.hm_score_waiting_body
import com.hopcape.odo.feature.dashboard.resources.hm_see_breakdown
import com.hopcape.odo.feature.dashboard.resources.hm_timeline
import com.hopcape.odo.feature.dashboard.resources.db_score_none
import org.jetbrains.compose.resources.stringResource

/**
 * The Home tab — the dashboard's cross-feature glance: health score, cost + savings,
 * what needs attention, an insight, and recent activity (or, for a new user, a setup
 * checklist). State-free; renders [state] and forwards intents to the shared routes
 * (health-score detail, documents, timeline, bill scanner…).
 */
@Composable
internal fun HomeScreen(
    state: HomeState,
    onSeeBreakdown: () -> Unit,
    onOpenAttention: () -> Unit,
    onTimeline: () -> Unit,
    onOpenRecent: (String) -> Unit,
    onBell: () -> Unit,
    onProfile: () -> Unit,
    onScanBill: () -> Unit,
    onAddDocuments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        bottomBar = {
            if (state is HomeState.NewUser) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = OdoTheme.spacing.screenEdge).padding(vertical = OdoTheme.spacing.md),
                ) {
                    OdoButton(stringResource(Res.string.hm_scan_first), onClick = onScanBill, modifier = Modifier.fillMaxWidth())
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            HomeHeader(
                userName = state.userName,
                carLine = state.carLine,
                showBell = state is HomeState.Scored,
                hasBadge = state is HomeState.Scored && state.hasNotification,
                onBell = onBell,
                onProfile = onProfile,
            )
            when (state) {
                is HomeState.Scored -> ScoredContent(state, onSeeBreakdown, onOpenAttention, onTimeline, onOpenRecent)
                is HomeState.NewUser -> NewUserContent(state, onScanBill, onAddDocuments)
            }
        }
    }
}

// --- Header ---------------------------------------------------------------------

@Composable
private fun HomeHeader(
    userName: String,
    carLine: String,
    showBell: Boolean,
    hasBadge: Boolean,
    onBell: () -> Unit,
    onProfile: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OdoText(stringResource(Res.string.hm_greeting, userName), style = OdoTheme.typography.title, maxLines = 1)
            OdoText(carLine, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim, maxLines = 1)
        }
        if (showBell) {
            Box {
                CircleButton(onClick = onBell) {
                    OdoIcon(IcBell, contentDescription = stringResource(Res.string.hm_cd_bell), size = OdoTheme.iconSizes.medium)
                }
                if (hasBadge) {
                    Box(Modifier.size(10.dp).align(Alignment.TopEnd).clip(CircleShape).background(OdoTheme.colors.accent))
                }
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(OdoTheme.colors.accent)
                .clickable(onClick = onProfile),
            contentAlignment = Alignment.Center,
        ) {
            OdoText(
                userName.take(1),
                style = OdoTheme.typography.heading,
                color = OdoTheme.colors.onAccent,
            )
        }
    }
}

@Composable
private fun CircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background(OdoTheme.colors.surfaceRaised).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

// --- Scored ---------------------------------------------------------------------

@Composable
private fun ScoredContent(
    state: HomeState.Scored,
    onSeeBreakdown: () -> Unit,
    onOpenAttention: () -> Unit,
    onTimeline: () -> Unit,
    onOpenRecent: (String) -> Unit,
) {
    HealthCard(state, onSeeBreakdown)
    StatsRow(state)
    InfoCard(state.attention, onClick = onOpenAttention)
    InfoCard(state.insight, onClick = {})
    RecentSection(state.recent, onTimeline, onOpenRecent)
}

@Composable
private fun HealthCard(state: HomeState.Scored, onSeeBreakdown: () -> Unit) {
    OdoCard {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            OdoHealthDial(
                score = state.score,
                dialSize = 128.dp,
                strokeWidth = 12.dp,
                arcColor = OdoTheme.colors.accent,
                centerContent = {
                    OdoText(
                        state.score.toString(),
                        style = OdoTheme.typography.display.copy(fontSize = 40.sp, lineHeight = 40.sp),
                    )
                    OdoText(state.band, style = OdoTheme.typography.caption, color = OdoTheme.colors.accent)
                },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(stringResource(Res.string.hm_health_score), style = OdoTheme.typography.caption, color = OdoTheme.colors.textDim)
                OdoText(state.healthNote, style = OdoTheme.typography.body)
                LinkRow(stringResource(Res.string.hm_see_breakdown), onSeeBreakdown)
            }
        }
    }
}

@Composable
private fun StatsRow(state: HomeState.Scored) {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
        StatCard(
            label = stringResource(Res.string.hm_running_cost),
            modifier = Modifier.weight(1f),
            value = {
                Row(verticalAlignment = Alignment.Bottom) {
                    OdoText(state.runningCost.formatRupeesDecimal(), style = OdoTheme.typography.title)
                    OdoText(stringResource(Res.string.hm_per_km), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
                }
            },
            footer = state.costDelta,
            footerColor = if (state.costAboveAvg) OdoTheme.colors.warning else OdoTheme.colors.success,
        )
        StatCard(
            label = stringResource(Res.string.hm_saved),
            modifier = Modifier.weight(1f),
            value = { OdoText(state.saved.formatRupees(), style = OdoTheme.typography.title, color = OdoTheme.colors.accent) },
            footer = state.savedSub,
            footerColor = OdoTheme.colors.textDim,
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: @Composable () -> Unit,
    footer: String,
    footerColor: Color,
    modifier: Modifier = Modifier,
) {
    OdoCard(modifier = modifier) {
        OdoText(label, style = OdoTheme.typography.caption, color = OdoTheme.colors.textDim, maxLines = 1)
        value()
        OdoText(footer, style = OdoTheme.typography.bodySmall, color = footerColor, maxLines = 1)
    }
}

@Composable
private fun InfoCard(card: HomeInfoCard, onClick: () -> Unit) {
    val colors = OdoTheme.colors
    val iconTint = when (card.tone) {
        HomeCardTone.WARNING -> colors.warning
        HomeCardTone.SUCCESS -> colors.success
        HomeCardTone.NEUTRAL -> colors.textDim
    }
    val border = when (card.tone) {
        HomeCardTone.WARNING -> BorderStroke(1.dp, colors.warning.copy(alpha = 0.4f))
        HomeCardTone.SUCCESS -> BorderStroke(1.dp, colors.success.copy(alpha = 0.35f))
        HomeCardTone.NEUTRAL -> BorderStroke(1.dp, colors.border)
    }
    OdoCard(onClick = if (card.hasChevron) onClick else null, border = border) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            HomeIconTile(card.icon, iconTint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (card.eyebrow != null) {
                    OdoText(card.eyebrow, style = OdoTheme.typography.caption, color = colors.accent)
                }
                OdoText(
                    card.title,
                    style = if (card.eyebrow != null) OdoTheme.typography.body else OdoTheme.typography.heading,
                )
                if (card.subtitle != null) {
                    OdoText(card.subtitle, style = OdoTheme.typography.bodySmall, color = colors.textDim)
                }
            }
            if (card.hasChevron) Chevron()
        }
    }
}

@Composable
private fun RecentSection(recent: HomeRecent, onTimeline: () -> Unit, onOpenRecent: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OdoText(stringResource(Res.string.hm_recent), style = OdoTheme.typography.caption, color = OdoTheme.colors.textDim, modifier = Modifier.weight(1f))
            LinkRow(stringResource(Res.string.hm_timeline), onTimeline)
        }
        OdoCard(onClick = { onOpenRecent(recent.logId) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
                HomeIconTile(recent.icon, OdoTheme.colors.textDim)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    OdoText(recent.title, style = OdoTheme.typography.heading, maxLines = 1)
                    OdoText(recent.meta, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim, maxLines = 1)
                }
                if (recent.amount != null) {
                    OdoText(recent.amount.formatRupees(), style = OdoTheme.typography.heading)
                }
            }
        }
    }
}

// --- New user -------------------------------------------------------------------

@Composable
private fun NewUserContent(state: HomeState.NewUser, onScanBill: () -> Unit, onAddDocuments: () -> Unit) {
    // Big waiting card: empty dial + copy.
    OdoCard {
        Column(
            Modifier.fillMaxWidth().padding(vertical = OdoTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            OdoHealthDial(
                score = 0,
                dialSize = 128.dp,
                strokeWidth = 12.dp,
                arcColor = OdoTheme.colors.border,
                centerContent = {
                    OdoText(
                        stringResource(Res.string.db_score_none),
                        style = OdoTheme.typography.display.copy(fontSize = 40.sp, lineHeight = 40.sp),
                        color = OdoTheme.colors.textMuted,
                    )
                },
            )
            OdoText(stringResource(Res.string.hm_score_waiting), style = OdoTheme.typography.heading, textAlign = TextAlign.Center)
            OdoText(stringResource(Res.string.hm_score_waiting_body), style = OdoTheme.typography.body, color = OdoTheme.colors.textDim, textAlign = TextAlign.Center)
        }
    }
    val done = state.checklist.count { it.done }
    OdoText(
        stringResource(Res.string.hm_get_set_up, done, state.checklist.size),
        style = OdoTheme.typography.caption,
        color = OdoTheme.colors.textDim,
    )
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
        state.checklist.forEachIndexed { index, item ->
            ChecklistCard(
                item = item,
                onClick = when (index) {
                    1 -> onScanBill
                    2 -> onAddDocuments
                    else -> null
                },
            )
        }
    }
}

@Composable
private fun ChecklistCard(item: HomeChecklistItem, onClick: (() -> Unit)?) {
    OdoCard(
        onClick = if (item.hasChevron) onClick else null,
        border = BorderStroke(1.dp, if (item.hasChevron) OdoTheme.colors.accent.copy(alpha = 0.4f) else OdoTheme.colors.border),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            CheckMark(done = item.done)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OdoText(
                    item.title,
                    style = OdoTheme.typography.heading.copy(
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    ),
                    color = if (item.done) OdoTheme.colors.textDim else OdoTheme.colors.text,
                )
                if (item.subtitle.isNotEmpty()) {
                    OdoText(item.subtitle, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
                }
            }
            if (item.hasChevron) Chevron()
        }
    }
}

@Composable
private fun CheckMark(done: Boolean) {
    if (done) {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(OdoTheme.colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(IcCheck, contentDescription = null, tint = OdoTheme.colors.onAccent, size = OdoTheme.iconSizes.small)
        }
    } else {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(OdoTheme.colors.surfaceRaised),
        )
    }
}

// --- Shared bits ----------------------------------------------------------------

@Composable
private fun HomeIconTile(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier.size(44.dp).clip(OdoTheme.shapes.field).background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        OdoIcon(icon, contentDescription = null, tint = tint, size = OdoTheme.iconSizes.medium)
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoText(label, style = OdoTheme.typography.label, color = OdoTheme.colors.accent)
        Chevron()
    }
}

/** A right-pointing chevron — CaretUp turned a quarter-turn — in the accent tint. */
@Composable
private fun Chevron() {
    OdoIcon(
        IcCaretUp,
        contentDescription = null,
        tint = OdoTheme.colors.accent,
        size = OdoTheme.iconSizes.small,
        modifier = Modifier.rotate(90f),
    )
}
