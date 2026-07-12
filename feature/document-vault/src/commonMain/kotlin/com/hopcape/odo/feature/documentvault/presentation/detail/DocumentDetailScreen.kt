package com.hopcape.odo.feature.documentvault.presentation.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoIconButton
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcClock
import com.hopcape.odo.core.designsystem.icons.IcDotsVertical
import com.hopcape.odo.core.designsystem.icons.IcEye
import com.hopcape.odo.core.designsystem.icons.IcPdf
import com.hopcape.odo.core.designsystem.icons.IcRefresh
import com.hopcape.odo.core.designsystem.icons.IcShare
import com.hopcape.odo.core.designsystem.icons.IcShield
import com.hopcape.odo.core.designsystem.icons.IcTrash
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.feature.documentvault.resources.Res
import com.hopcape.odo.feature.documentvault.resources.dv_cd_more
import com.hopcape.odo.feature.documentvault.resources.dv_cover_engine
import com.hopcape.odo.feature.documentvault.resources.dv_cover_own_damage
import com.hopcape.odo.feature.documentvault.resources.dv_cover_third_party
import com.hopcape.odo.feature.documentvault.resources.dv_cover_zero_dep
import com.hopcape.odo.feature.documentvault.resources.dv_detail_cover_type
import com.hopcape.odo.feature.documentvault.resources.dv_detail_days_suffix
import com.hopcape.odo.feature.documentvault.resources.dv_detail_expires_in
import com.hopcape.odo.feature.documentvault.resources.dv_detail_idv_label
import com.hopcape.odo.feature.documentvault.resources.dv_detail_premium
import com.hopcape.odo.feature.documentvault.resources.dv_detail_renew
import com.hopcape.odo.feature.documentvault.resources.dv_detail_verified
import com.hopcape.odo.feature.documentvault.resources.dv_detail_view
import com.hopcape.odo.feature.documentvault.resources.dv_detail_whats_covered
import com.hopcape.odo.feature.documentvault.resources.dv_menu_delete
import com.hopcape.odo.feature.documentvault.resources.dv_menu_download
import com.hopcape.odo.feature.documentvault.resources.dv_menu_replace
import com.hopcape.odo.feature.documentvault.resources.dv_menu_share
import com.hopcape.odo.feature.documentvault.resources.dv_status_valid_till
import org.jetbrains.compose.resources.stringResource

/**
 * A single document's detail — provider + policy header, expiry countdown, cover type /
 * premium, the "what's covered" breakdown, and file actions (replace / share / download /
 * delete) behind the overflow menu, with "Renew now" pinned to the bottom.
 *
 * State-free: renders [state] and forwards intents. The nav back button is the plain
 * top-bar chevron (no circular chrome); real documents + file actions land in M2.
 */
@Composable
internal fun DocumentDetailScreen(
    state: DocumentDetailUiState,
    onView: () -> Unit,
    onRenew: () -> Unit,
    onReplace: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = state.docName,
        onBack = onBack,
        actions = { DocumentMenu(onReplace = onReplace, onShare = onShare, onDownload = onDownload, onDelete = onDelete) },
        bottomBar = { RenewBar(onRenew) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            HeroCard(state = state, onView = onView)
            ExpiryCard(state = state)
            Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
                StatCard(stringResource(Res.string.dv_detail_cover_type), state.coverType, Modifier.weight(1f))
                StatCard(stringResource(Res.string.dv_detail_premium), state.premiumPerYear.formatRupees(), Modifier.weight(1f))
            }
            CoveredSection(state.covers)
        }
    }
}

@Composable
private fun RowScope.DocumentMenu(onReplace: () -> Unit, onShare: () -> Unit, onDownload: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OdoIconButton(IcDotsVertical, contentDescription = stringResource(Res.string.dv_cd_more), onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuItem(stringResource(Res.string.dv_menu_replace), IcRefresh) { expanded = false; onReplace() }
            MenuItem(stringResource(Res.string.dv_menu_share), IcShare) { expanded = false; onShare() }
            MenuItem(stringResource(Res.string.dv_menu_download), IcPdf) { expanded = false; onDownload() }
            HorizontalDivider(color = OdoTheme.colors.border)
            MenuItem(stringResource(Res.string.dv_menu_delete), IcTrash, tint = OdoTheme.colors.danger) { expanded = false; onDelete() }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, tint: Color = OdoTheme.colors.text, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { OdoText(label, style = OdoTheme.typography.body, color = tint) },
        leadingIcon = { OdoIcon(icon, contentDescription = null, tint = tint, size = OdoTheme.iconSizes.small) },
        onClick = onClick,
    )
}

@Composable
private fun HeroCard(state: DocumentDetailUiState, onView: () -> Unit) {
    OdoCard(
        color = OdoTheme.colors.accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, OdoTheme.colors.accent.copy(alpha = 0.35f)),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.Top) {
            IconChip(IcShield, OdoTheme.colors.accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(state.provider, style = OdoTheme.typography.heading)
                OdoText(state.subtitle, style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
            }
            if (state.verified) {
                OdoBadge(
                    text = stringResource(Res.string.dv_detail_verified),
                    tone = OdoBadgeTone.Success,
                    leadingIcon = { OdoIcon(IcCheck, contentDescription = null, size = OdoTheme.iconSizes.small) },
                )
            }
        }
        OdoText(
            state.policyNumber,
            style = OdoTheme.typography.numeric.copy(letterSpacing = 3.sp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(stringResource(Res.string.dv_detail_idv_label), style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
                OdoText(state.sumInsured.formatRupees(), style = OdoTheme.typography.title)
            }
            ViewButton(onView)
        }
    }
}

@Composable
private fun ViewButton(onView: () -> Unit) {
    Box(
        Modifier
            .clip(OdoTheme.shapes.pill)
            .background(OdoTheme.colors.surfaceRaised)
            .clickable(onClick = onView)
            .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            OdoIcon(IcEye, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.small)
            OdoText(stringResource(Res.string.dv_detail_view), style = OdoTheme.typography.label)
        }
    }
}

@Composable
private fun ExpiryCard(state: DocumentDetailUiState) {
    val tone = OdoTheme.colors.warning
    OdoCard(
        color = tone.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.35f)),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconChip(IcClock, tone)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(stringResource(Res.string.dv_detail_expires_in, state.expiresInDays), style = OdoTheme.typography.heading)
                OdoText(stringResource(Res.string.dv_status_valid_till, formatDate(state.validTill)), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                OdoText("${state.expiresInDays}", style = OdoTheme.typography.title, color = tone, modifier = Modifier.alignByBaseline())
                OdoText(stringResource(Res.string.dv_detail_days_suffix), style = OdoTheme.typography.body, color = tone, modifier = Modifier.alignByBaseline())
            }
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(OdoTheme.shapes.pill).background(OdoTheme.colors.surfaceRaised)) {
            Box(
                Modifier
                    .fillMaxWidth(state.validityProgress.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(OdoTheme.shapes.pill)
                    .background(Brush.horizontalGradient(listOf(OdoTheme.colors.success, tone))),
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    OdoCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
        OdoText(label, style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
        OdoText(value, style = OdoTheme.typography.heading)
    }
}

@Composable
private fun CoveredSection(covers: List<CoverItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        OdoText(stringResource(Res.string.dv_detail_whats_covered), style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        ) {
            covers.forEach { CoverChip(coverLabel(it.kind), it.covered) }
        }
    }
}

@Composable
private fun CoverChip(label: String, covered: Boolean) {
    Box(
        Modifier
            .clip(OdoTheme.shapes.pill)
            .border(1.dp, OdoTheme.colors.border, OdoTheme.shapes.pill)
            .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            if (covered) {
                OdoIcon(IcCheck, contentDescription = null, tint = OdoTheme.colors.success, size = OdoTheme.iconSizes.small)
            } else {
                OdoText("—", style = OdoTheme.typography.label, color = OdoTheme.colors.textMuted)
            }
            OdoText(label, style = OdoTheme.typography.label, color = if (covered) OdoTheme.colors.text else OdoTheme.colors.textMuted)
        }
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
private fun RenewBar(onRenew: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(OdoTheme.colors.bg)
            .navigationBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(top = OdoTheme.spacing.sm, bottom = OdoTheme.spacing.md),
    ) {
        OdoButton(stringResource(Res.string.dv_detail_renew), onClick = onRenew, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun coverLabel(kind: CoverKind): String = stringResource(
    when (kind) {
        CoverKind.OWN_DAMAGE -> Res.string.dv_cover_own_damage
        CoverKind.THIRD_PARTY -> Res.string.dv_cover_third_party
        CoverKind.ZERO_DEP -> Res.string.dv_cover_zero_dep
        CoverKind.ENGINE -> Res.string.dv_cover_engine
    },
)

@OdoThemePreviews
@Composable
private fun DocumentDetailScreenPreview() = OdoPreview(padded = false) {
    DocumentDetailScreen(
        state = sampleDocumentDetail(),
        onView = {}, onRenew = {}, onReplace = {}, onShare = {}, onDownload = {}, onDelete = {}, onBack = {},
    )
}
