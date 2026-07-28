package com.hopcape.odo.feature.profile.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcBarChart
import com.hopcape.odo.core.designsystem.icons.IcBell
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcEye
import com.hopcape.odo.core.designsystem.icons.IcInfo
import com.hopcape.odo.core.designsystem.icons.IcLock
import com.hopcape.odo.core.designsystem.icons.IcShare
import com.hopcape.odo.core.designsystem.icons.IcSignOut
import com.hopcape.odo.core.designsystem.icons.IcStar
import com.hopcape.odo.core.designsystem.icons.IcTag
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_appearance
import com.hopcape.odo.feature.profile.resources.pf_data_privacy
import com.hopcape.odo.feature.profile.resources.pf_edit
import com.hopcape.odo.feature.profile.resources.pf_export
import com.hopcape.odo.feature.profile.resources.pf_free_plan
import com.hopcape.odo.feature.profile.resources.pf_go_pro
import com.hopcape.odo.feature.profile.resources.pf_help
import com.hopcape.odo.feature.profile.resources.pf_manage_plan
import com.hopcape.odo.feature.profile.resources.pf_notifications
import com.hopcape.odo.feature.profile.resources.pf_passport
import com.hopcape.odo.feature.profile.resources.pf_passport_owned
import com.hopcape.odo.feature.profile.resources.pf_preferences
import com.hopcape.odo.feature.profile.resources.pf_privacy
import com.hopcape.odo.feature.profile.resources.pf_pro_active
import com.hopcape.odo.feature.profile.resources.pf_pro_feat_1
import com.hopcape.odo.feature.profile.resources.pf_pro_feat_2
import com.hopcape.odo.feature.profile.resources.pf_pro_feat_3
import com.hopcape.odo.feature.profile.resources.pf_pro_title
import com.hopcape.odo.feature.profile.resources.pf_restore
import com.hopcape.odo.feature.profile.resources.pf_sign_out
import com.hopcape.odo.feature.profile.resources.pf_start_pro
import com.hopcape.odo.feature.profile.resources.pf_title
import com.hopcape.odo.feature.profile.resources.pf_units
import com.hopcape.odo.feature.profile.resources.pf_version
import org.jetbrains.compose.resources.stringResource

/**
 * The Profile / account home. Switches between the Pro-plan card and the Go-Pro upsell,
 * then the preference + data rows. State-free; each row navigates to its screen/sheet,
 * and "Go Pro" / "Manage plan" jump to the shared [Paywall] route.
 */
@Composable
internal fun ProfileScreen(
    state: ProfileUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onGoPro: () -> Unit,
    onRestore: () -> Unit,
    onNotifications: () -> Unit,
    onUnits: () -> Unit,
    onAppearance: () -> Unit,
    onExport: () -> Unit,
    onPrivacy: () -> Unit,
    onHelp: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(modifier = modifier, title = stringResource(Res.string.pf_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            ProfileCard(state, onEdit)
            if (state.isPro) ProPlanCard(state, onGoPro, onRestore) else GoProCard(onGoPro, onRestore)

            SectionLabel(stringResource(Res.string.pf_preferences))
            SettingsGroup {
                SettingsRow(IcBell, stringResource(Res.string.pf_notifications), onNotifications, value = state.notificationsSummary)
                RowDivider()
                SettingsRow(IcBarChart, stringResource(Res.string.pf_units), onUnits, value = state.unitsSummary)
                RowDivider()
                SettingsRow(IcEye, stringResource(Res.string.pf_appearance), onAppearance, value = state.appearanceSummary)
            }

            SectionLabel(stringResource(Res.string.pf_data_privacy))
            SettingsGroup {
                SettingsRow(IcShare, stringResource(Res.string.pf_export), onExport)
                RowDivider()
                SettingsRow(IcLock, stringResource(Res.string.pf_privacy), onPrivacy)
            }

            Spacer(Modifier.height(OdoTheme.spacing.md))
            SettingsGroup {
                SettingsRow(IcInfo, stringResource(Res.string.pf_help), onHelp)
                RowDivider()
                SettingsRow(IcSignOut, stringResource(Res.string.pf_sign_out), onSignOut, danger = true, showChevron = false)
            }

            OdoText(
                stringResource(Res.string.pf_version),
                style = OdoTheme.typography.caption,
                color = OdoTheme.colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = OdoTheme.spacing.sm),
            )
        }
    }
}

@Composable
private fun ProfileCard(state: ProfileUiState, onEdit: () -> Unit) {
    OdoCard {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Avatar(state.name.take(1))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OdoText(state.name, style = OdoTheme.typography.heading, maxLines = 1)
                OdoText(state.phoneLine, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim, maxLines = 1)
            }
            OdoButton(stringResource(Res.string.pf_edit), onClick = onEdit, variant = OdoButtonVariant.Secondary)
        }
    }
}

@Composable
private fun ProPlanCard(state: ProfileUiState, onManage: () -> Unit, onRestore: () -> Unit) {
    OdoCard(border = BorderStroke(1.dp, OdoTheme.colors.accent.copy(alpha = 0.4f))) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconTile(IcStar)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OdoText(stringResource(Res.string.pf_pro_title), style = OdoTheme.typography.heading)
                OdoText(state.proRenews, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            }
            OdoBadge(stringResource(Res.string.pf_pro_active), tone = OdoBadgeTone.Accent)
        }
        OdoDivider(Modifier.padding(vertical = OdoTheme.spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            OdoIcon(IcTag, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.small)
            OdoText(stringResource(Res.string.pf_passport), style = OdoTheme.typography.body, modifier = Modifier.weight(1f))
            OdoText(stringResource(Res.string.pf_passport_owned), style = OdoTheme.typography.label, color = OdoTheme.colors.accent)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            OdoButton(stringResource(Res.string.pf_manage_plan), onClick = onManage, modifier = Modifier.weight(1f), variant = OdoButtonVariant.Secondary)
            OdoButton(stringResource(Res.string.pf_restore), onClick = onRestore, modifier = Modifier.weight(1f), variant = OdoButtonVariant.Secondary)
        }
    }
}

@Composable
private fun GoProCard(onStartPro: () -> Unit, onRestore: () -> Unit) {
    OdoCard(border = BorderStroke(1.dp, OdoTheme.colors.accent.copy(alpha = 0.4f))) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconTile(IcStar)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OdoText(stringResource(Res.string.pf_go_pro), style = OdoTheme.typography.heading)
                OdoText(stringResource(Res.string.pf_free_plan), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            }
        }
        FeatureRow(stringResource(Res.string.pf_pro_feat_1))
        FeatureRow(stringResource(Res.string.pf_pro_feat_2))
        FeatureRow(stringResource(Res.string.pf_pro_feat_3))
        OdoButton(stringResource(Res.string.pf_start_pro), onClick = onStartPro, modifier = Modifier.fillMaxWidth().padding(top = OdoTheme.spacing.xs))
        OdoText(
            stringResource(Res.string.pf_restore),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onRestore),
        )
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        OdoIcon(IcCheck, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.small)
        OdoText(text, style = OdoTheme.typography.body)
    }
}
