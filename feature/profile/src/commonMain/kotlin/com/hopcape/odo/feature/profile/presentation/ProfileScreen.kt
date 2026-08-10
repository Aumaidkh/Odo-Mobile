package com.hopcape.odo.feature.profile.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.common.FeatureFlags
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcBellOutlined
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcEyeFilled
import com.hopcape.odo.core.designsystem.icons.IcInfo
import com.hopcape.odo.core.designsystem.icons.IcLockFilled
import com.hopcape.odo.core.designsystem.icons.IcRupee
import com.hopcape.odo.core.designsystem.icons.IcShare
import com.hopcape.odo.core.designsystem.icons.IcPerson
import com.hopcape.odo.core.designsystem.icons.IcSignOut
import com.hopcape.odo.core.designsystem.icons.IcStarFilled
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.settings.model.ThemePreference
import com.hopcape.odo.core.domain.shared.suffix
import com.hopcape.odo.feature.profile.presentation.state.Loadable
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_appear_dark
import com.hopcape.odo.feature.profile.resources.pf_appear_light
import com.hopcape.odo.feature.profile.resources.pf_appear_system
import com.hopcape.odo.feature.profile.resources.pf_appearance
import com.hopcape.odo.feature.profile.resources.pf_cd_back
import com.hopcape.odo.feature.profile.resources.pf_city_missing
import com.hopcape.odo.feature.profile.resources.pf_data_privacy
import com.hopcape.odo.feature.profile.resources.pf_edit
import com.hopcape.odo.feature.profile.resources.pf_export
import com.hopcape.odo.feature.profile.resources.pf_free_plan
import com.hopcape.odo.feature.profile.resources.pf_go_pro
import com.hopcape.odo.feature.profile.resources.pf_help
import com.hopcape.odo.feature.profile.resources.pf_manage_plan
import com.hopcape.odo.feature.profile.resources.pf_name_missing
import com.hopcape.odo.feature.profile.resources.pf_notif_summary
import com.hopcape.odo.feature.profile.resources.pf_notifications
import com.hopcape.odo.feature.profile.resources.pf_preferences
import com.hopcape.odo.feature.profile.resources.pf_privacy
import com.hopcape.odo.feature.profile.resources.pf_pro_active
import com.hopcape.odo.feature.profile.resources.pf_pro_feat_1
import com.hopcape.odo.feature.profile.resources.pf_pro_feat_2
import com.hopcape.odo.feature.profile.resources.pf_pro_feat_3
import com.hopcape.odo.feature.profile.resources.pf_pro_title
import com.hopcape.odo.feature.profile.resources.pf_restore
import com.hopcape.odo.feature.profile.resources.pf_sign_in
import com.hopcape.odo.feature.profile.resources.pf_sign_out
import com.hopcape.odo.feature.profile.resources.pf_start_pro
import com.hopcape.odo.feature.profile.resources.pf_title
import com.hopcape.odo.feature.profile.resources.pf_units
import com.hopcape.odo.feature.profile.resources.pf_units_summary
import com.hopcape.odo.feature.profile.resources.pf_sync_title
import com.hopcape.odo.feature.profile.resources.pf_sync_idle
import com.hopcape.odo.feature.profile.resources.pf_sync_running
import com.hopcape.odo.feature.profile.resources.pf_sync_pending
import com.hopcape.odo.feature.profile.resources.pf_sync_pending_plural
import com.hopcape.odo.feature.profile.resources.pf_sync_never
import com.hopcape.odo.feature.profile.resources.pf_sync_blocked
import com.hopcape.odo.feature.profile.resources.pf_sync_last
import com.hopcape.odo.feature.profile.resources.pf_version
import com.hopcape.odo.feature.profile.resources.pf_version_build
import org.jetbrains.compose.resources.stringResource

/**
 * The Profile / account home. Switches between the Pro-plan card and the Go-Pro upsell,
 * then the preference + data rows. Each row navigates to its screen or sheet, and
 * "Go Pro" / "Manage plan" jump to the shared Paywall route.
 *
 * The last row is sign-in or sign-out depending on whether this device has a session.
 * Offering "Sign out" to someone who never signed in is an action that cannot do anything.
 */
@Composable
internal fun ProfileScreen(
    state: ProfileUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onGoPro: () -> Unit,
    onNotifications: () -> Unit,
    onUnits: () -> Unit,
    onAppearance: () -> Unit,
    onExport: () -> Unit,
    onPrivacy: () -> Unit,
    onHelp: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.pf_title),
        onBack = onBack,
        backContentDescription = stringResource(Res.string.pf_cd_back),
    ) { padding ->
        when (val content = state.content) {
            Loadable.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { OdoLoadingIndicator() }

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

            is Loadable.Ready -> ProfileContentColumn(
                content = content.value,
                version = state.version,
                buildNumber = state.buildNumber,
                sync = state.sync,
                padding = padding,
                onEdit = onEdit,
                onGoPro = onGoPro,
                onNotifications = onNotifications,
                onUnits = onUnits,
                onAppearance = onAppearance,
                onExport = onExport,
                onPrivacy = onPrivacy,
                onHelp = onHelp,
                onSignIn = onSignIn,
                onSignOut = onSignOut,
            )
        }
    }
}

@Composable
private fun ProfileContentColumn(
    content: ProfileContent,
    version: String,
    buildNumber: Long?,
    sync: SyncStatus,
    padding: PaddingValues,
    onEdit: () -> Unit,
    onGoPro: () -> Unit,
    onNotifications: () -> Unit,
    onUnits: () -> Unit,
    onAppearance: () -> Unit,
    onExport: () -> Unit,
    onPrivacy: () -> Unit,
    onHelp: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(vertical = OdoTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        ProfileCard(content, onEdit)
        // The plan card is the only place that names Odo Pro, the free plan and a price, so
        // it is hidden while nothing can sell a subscription. Both cards are still here and
        // come back with FeatureFlags.PAYWALL_ENABLED.
        if (FeatureFlags.PAYWALL_ENABLED) {
            if (content.isPro) ProPlanCard(onGoPro) else GoProCard(onGoPro)
        }

        SectionLabel(stringResource(Res.string.pf_preferences))
        SettingsGroup {
            SettingsRow(
                icon = IcBellOutlined,
                title = stringResource(Res.string.pf_notifications),
                onClick = onNotifications,
                value = stringResource(Res.string.pf_notif_summary, content.notificationTopicsOn),
                testTag = ProfileTestTags.NOTIFICATIONS_ROW,
            )
            RowDivider()
            SettingsRow(
                icon = IcRupee,
                title = stringResource(Res.string.pf_units),
                onClick = onUnits,
                value = stringResource(Res.string.pf_units_summary, content.distanceUnit.suffix()),
                testTag = ProfileTestTags.UNITS_ROW,
            )
            RowDivider()
            SettingsRow(
                icon = IcEyeFilled,
                title = stringResource(Res.string.pf_appearance),
                onClick = onAppearance,
                value = content.theme.label(),
                testTag = ProfileTestTags.APPEARANCE_ROW,
            )
        }

        SectionLabel(stringResource(Res.string.pf_data_privacy))
        SettingsGroup {
            SettingsRow(IcShare, stringResource(Res.string.pf_export), onExport)
            RowDivider()
            SettingsRow(IcLockFilled, stringResource(Res.string.pf_privacy), onPrivacy)
        }

        Spacer(Modifier.height(OdoTheme.spacing.md))
        SettingsGroup {
            SettingsRow(IcInfo, stringResource(Res.string.pf_help), onHelp)
            RowDivider()
            if (content.isSignedIn) {
                SettingsRow(
                    icon = IcSignOut,
                    title = stringResource(Res.string.pf_sign_out),
                    onClick = onSignOut,
                    danger = true,
                    showChevron = false,
                    testTag = ProfileTestTags.SESSION_ROW,
                )
            } else {
                SettingsRow(
                    icon = IcPerson,
                    title = stringResource(Res.string.pf_sign_in),
                    onClick = onSignIn,
                    testTag = ProfileTestTags.SESSION_ROW,
                )
            }
        }

        SyncDebugRow(sync)

        OdoText(
            // The build number only shows where it helps: a tester on a stage build has
            // several builds of the same version, an owner on the store has one.
            if (buildNumber == null) {
                stringResource(Res.string.pf_version, version)
            } else {
                stringResource(Res.string.pf_version_build, version, buildNumber.toString())
            },
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = OdoTheme.spacing.sm),
        )
    }
}

@Composable
private fun ProfileCard(content: ProfileContent, onEdit: () -> Unit) {
    OdoCard {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Avatar(initialOf(content.name), photoKey = content.avatarPath)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OdoText(
                    content.name ?: stringResource(Res.string.pf_name_missing),
                    style = OdoTheme.typography.heading,
                    color = if (content.name != null) OdoTheme.colors.text else OdoTheme.colors.textDim,
                    maxLines = 1,
                )
                OdoText(
                    content.subtitle(),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                    maxLines = 1,
                )
            }
            OdoButton(stringResource(Res.string.pf_edit), onClick = onEdit, variant = OdoButtonVariant.Secondary)
        }
    }
}

/**
 * The line under the name: the owner's city, or the prompt to set one.
 *
 * The city earns the line because it is what turns price checks on, and an owner without
 * one gets no verdicts at all. Whether they are signed in is said by the last row on the
 * screen, so it does not compete for this line.
 */
@Composable
private fun ProfileContent.subtitle(): String =
    city ?: stringResource(Res.string.pf_city_missing)

@Composable
private fun ThemePreference.label(): String = when (this) {
    ThemePreference.DARK -> stringResource(Res.string.pf_appear_dark)
    ThemePreference.LIGHT -> stringResource(Res.string.pf_appear_light)
    ThemePreference.SYSTEM -> stringResource(Res.string.pf_appear_system)
}

/** The monogram shown when there is no photo. Falls back to Odo's own initial. */
private fun initialOf(name: String?): String = name?.trim()?.firstOrNull()?.uppercase() ?: "O"

/**
 * The Pro card. It states the plan and nothing about its renewal: there is no subscription
 * record to read one from, and a date the app invented would be worse than none.
 */
@Composable
private fun ProPlanCard(onManage: () -> Unit) {
    OdoCard(border = BorderStroke(1.dp, OdoTheme.colors.accent.copy(alpha = 0.4f))) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconTile(IcStarFilled)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OdoText(stringResource(Res.string.pf_pro_title), style = OdoTheme.typography.heading)
                OdoText(
                    stringResource(Res.string.pf_pro_feat_2),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
            OdoBadge(stringResource(Res.string.pf_pro_active), tone = OdoBadgeTone.Accent)
        }
        OdoDivider(Modifier.padding(vertical = OdoTheme.spacing.xs))
        OdoButton(
            stringResource(Res.string.pf_manage_plan),
            onClick = onManage,
            modifier = Modifier.fillMaxWidth(),
            variant = OdoButtonVariant.Secondary,
        )
    }
}

/**
 * The upsell. "Restore purchases" goes to the same place as "Start Pro" on purpose: nothing
 * can be restored until Razorpay lands, and the paywall is where both the purchase and its
 * restore will live, so the row leads somewhere real rather than doing nothing.
 */
@Composable
private fun GoProCard(onStartPro: () -> Unit) {
    OdoCard(border = BorderStroke(1.dp, OdoTheme.colors.accent.copy(alpha = 0.4f))) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconTile(IcStarFilled)
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
            modifier = Modifier.fillMaxWidth().clickable(onClick = onStartPro),
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

/**
 * Where sync has got to, in one line under the version.
 *
 * A developer row, and it reads like one on purpose — this is what someone is asked to read
 * out when they say their data has not appeared on another device (SYNC_DESIGN §11). It
 * shows the pending count even when everything is fine, because "0 changes waiting" and
 * "sync has never run" look identical from the outside otherwise.
 */
@Composable
private fun SyncDebugRow(sync: SyncStatus) {
    val status = when {
        sync.isSyncing -> stringResource(Res.string.pf_sync_running)
        sync.pendingCount == 1 -> stringResource(Res.string.pf_sync_pending, sync.pendingCount)
        sync.pendingCount > 1 -> stringResource(Res.string.pf_sync_pending_plural, sync.pendingCount)
        else -> stringResource(Res.string.pf_sync_idle)
    }
    val last = sync.lastSyncedAt
        ?.let { stringResource(Res.string.pf_sync_last, it.toString()) }
        ?: stringResource(Res.string.pf_sync_never)
    // A pending count on its own cannot say whether an upload is in progress or has been
    // refused the same way for three days, and those look identical to whoever is asking
    // why their data is missing.
    val blocked = sync.lastError?.let { " · " + stringResource(Res.string.pf_sync_blocked, it) }.orEmpty()

    OdoText(
        "${stringResource(Res.string.pf_sync_title)}: $status · $last$blocked",
        style = OdoTheme.typography.caption,
        color = if (sync.lastError == null) OdoTheme.colors.textMuted else OdoTheme.colors.danger,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = OdoTheme.spacing.sm)
            .testTag(ProfileTestTags.SYNC_ROW),
    )
}
