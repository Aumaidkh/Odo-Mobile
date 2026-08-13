package com.hopcape.odo.feature.profile.presentation.privacy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcBellOutlined
import com.hopcape.odo.core.designsystem.icons.IcCamera
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcFileFilled
import com.hopcape.odo.core.designsystem.icons.IcGarage
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.platform.permission.PlatformPermission
import com.hopcape.odo.core.platform.permission.rememberPermissionController
import com.hopcape.odo.feature.profile.presentation.ProfileTestTags
import com.hopcape.odo.feature.profile.presentation.RowDivider
import com.hopcape.odo.feature.profile.presentation.SettingsGroup
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_pv_allowed
import com.hopcape.odo.feature.profile.resources.pf_pv_always
import com.hopcape.odo.feature.profile.resources.pf_pv_blocked
import com.hopcape.odo.feature.profile.resources.pf_pv_camera
import com.hopcape.odo.feature.profile.resources.pf_pv_camera_sub
import com.hopcape.odo.feature.profile.resources.pf_pv_each_time
import com.hopcape.odo.feature.profile.resources.pf_pv_files
import com.hopcape.odo.feature.profile.resources.pf_pv_files_sub
import com.hopcape.odo.feature.profile.resources.pf_pv_location
import com.hopcape.odo.feature.profile.resources.pf_pv_location_sub
import com.hopcape.odo.feature.profile.resources.pf_pv_managed
import com.hopcape.odo.feature.profile.resources.pf_pv_not_allowed
import com.hopcape.odo.feature.profile.resources.pf_pv_notifications
import com.hopcape.odo.feature.profile.resources.pf_pv_notifications_sub
import com.hopcape.odo.feature.profile.resources.pf_pv_while_using
import org.jetbrains.compose.resources.stringResource

/**
 * What Odo can reach on this device, and nothing the owner can change from here.
 *
 * Read-only on purpose. Android owns these switches, and a toggle in Odo would either be a
 * shortcut that silently fails (the system will not re-prompt a blocked permission) or a lie.
 * Tapping a row — or the footnote — opens the app's system settings page, which is where the
 * real control lives.
 *
 * The statuses come from `rememberPermissionController`, which is a composable rather than an
 * injected port because asking for a permission needs the Activity hosting the UI. That is
 * also why this section takes no state from the ViewModel: its `status` is Compose state that
 * re-reads on return from settings, so the rows are correct without anyone polling.
 */
@Composable
internal fun DeviceAccessSection(modifier: Modifier = Modifier) {
    val camera = rememberPermissionController(PlatformPermission.CAMERA)
    val notifications = rememberPermissionController(PlatformPermission.POST_NOTIFICATIONS)
    // Two controllers for one row. "While using" and "Always" are different grants and
    // PermissionStatus cannot express the difference on its own — it answers per permission,
    // and background location is a separate one.
    val fineLocation = rememberPermissionController(PlatformPermission.ACCESS_FINE_LOCATION)
    val backgroundLocation = rememberPermissionController(PlatformPermission.ACCESS_BACKGROUND_LOCATION)

    val openSettings = { camera.openAppSettings() }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        SettingsGroup {
            AccessRow(
                icon = IcCamera,
                title = stringResource(Res.string.pf_pv_camera),
                subtitle = stringResource(Res.string.pf_pv_camera_sub),
                value = camera.status.label(),
                granted = camera.status == PermissionStatus.Granted,
                onClick = openSettings,
                testTag = ProfileTestTags.PRIVACY_CAMERA_ROW,
            )
           // RowDivider()
            /*
 AccessRow(
     icon = IcGarage,
     title = stringResource(Res.string.pf_pv_location),
     subtitle = stringResource(Res.string.pf_pv_location_sub),
     value = locationLabel(fineLocation.status, backgroundLocation.status),
     granted = fineLocation.status == PermissionStatus.Granted,
     onClick = openSettings,
     testTag = ProfileTestTags.PRIVACY_LOCATION_ROW,
 )
 */
            RowDivider()
            AccessRow(
                icon = IcBellOutlined,
                title = stringResource(Res.string.pf_pv_notifications),
                subtitle = stringResource(Res.string.pf_pv_notifications_sub),
                value = notifications.status.label(),
                granted = notifications.status == PermissionStatus.Granted,
                onClick = openSettings,
                testTag = ProfileTestTags.PRIVACY_NOTIFICATIONS_ROW,
            )
            RowDivider()
            // Static, and it earns its place by saying so. Picking a file goes through the
            // system document picker, which grants access to the one document chosen and
            // holds no permission — there is nothing here that could be on or off.
            AccessRow(
                icon = IcFileFilled,
                title = stringResource(Res.string.pf_pv_files),
                subtitle = stringResource(Res.string.pf_pv_files_sub),
                value = stringResource(Res.string.pf_pv_each_time),
                granted = false,
                onClick = null,
                testTag = ProfileTestTags.PRIVACY_FILES_ROW,
            )
        }

        OdoText(
            stringResource(Res.string.pf_pv_managed),
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textMuted,
            modifier = Modifier
                .padding(horizontal = OdoTheme.spacing.xs)
                .clickable(onClick = openSettings),
        )
    }
}

/**
 * One device-access row: icon, what it is for, and where it stands.
 *
 * No chevron even when tappable. A chevron promises a screen of Odo's own, and what is on
 * the other side of this tap is the Android settings app.
 */
@Composable
private fun AccessRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
    granted: Boolean,
    onClick: (() -> Unit)?,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .heightIn(min = OdoTheme.spacing.minTouchTarget)
            .padding(vertical = OdoTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIcon(icon, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.medium)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OdoText(title, style = OdoTheme.typography.heading, maxLines = 1)
            OdoText(subtitle, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim, maxLines = 1)
        }
        StatusLabel(value, granted)
    }
}

/**
 * The trailing state, ticked when the permission is held.
 *
 * The tick is the only colour on the row, and it is reserved for a grant. A denial is not a
 * warning — it is a choice the owner made, and rendering it in red would read as a fault.
 */
@Composable
private fun StatusLabel(value: String, granted: Boolean) {
    val tint: Color = if (granted) OdoTheme.colors.accent else OdoTheme.colors.textMuted
    Row(
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (granted) {
            OdoIcon(IcCheck, contentDescription = null, tint = tint, size = OdoTheme.iconSizes.small)
        }
        OdoText(value, style = OdoTheme.typography.bodySmall, color = tint)
    }
}

@Composable
private fun PermissionStatus.label(): String = when (this) {
    PermissionStatus.Granted -> stringResource(Res.string.pf_pv_allowed)
    PermissionStatus.Askable -> stringResource(Res.string.pf_pv_not_allowed)
    // Told apart from a plain denial because only one of them can be undone from inside the
    // app, and the row's tap goes to system settings precisely for this case.
    PermissionStatus.Blocked -> stringResource(Res.string.pf_pv_blocked)
}

/**
 * The location row's state, from the two grants behind it.
 *
 * Background location without foreground is not a state Android can be in, so the fine grant
 * is what decides whether anything is allowed at all; the background one only upgrades the
 * wording from "while using" to "always".
 */
@Composable
private fun locationLabel(fine: PermissionStatus, background: PermissionStatus): String = when {
    fine != PermissionStatus.Granted -> fine.label()
    background == PermissionStatus.Granted -> stringResource(Res.string.pf_pv_always)
    else -> stringResource(Res.string.pf_pv_while_using)
}
