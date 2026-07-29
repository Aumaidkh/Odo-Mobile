package com.hopcape.odo.feature.profile.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoSwitch
import com.hopcape.odo.core.designsystem.component.OdoSwitchRow
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcEnvelope
import com.hopcape.odo.core.designsystem.icons.IcInfo
import com.hopcape.odo.core.designsystem.icons.IcWindow
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_notif_channels
import com.hopcape.odo.feature.profile.resources.pf_notif_custom
import com.hopcape.odo.feature.profile.resources.pf_notif_custom_sub
import com.hopcape.odo.feature.profile.resources.pf_notif_device
import com.hopcape.odo.feature.profile.resources.pf_notif_doc_expiry
import com.hopcape.odo.feature.profile.resources.pf_notif_doc_expiry_sub
import com.hopcape.odo.feature.profile.resources.pf_notif_email
import com.hopcape.odo.feature.profile.resources.pf_notif_health
import com.hopcape.odo.feature.profile.resources.pf_notif_health_sub
import com.hopcape.odo.feature.profile.resources.pf_notif_insights
import com.hopcape.odo.feature.profile.resources.pf_notif_monthly
import com.hopcape.odo.feature.profile.resources.pf_notif_monthly_sub
import com.hopcape.odo.feature.profile.resources.pf_notif_overcharge
import com.hopcape.odo.feature.profile.resources.pf_notif_overcharge_sub
import com.hopcape.odo.feature.profile.resources.pf_notif_push
import com.hopcape.odo.feature.profile.resources.pf_notif_reminders
import com.hopcape.odo.feature.profile.resources.pf_notif_service_due
import com.hopcape.odo.feature.profile.resources.pf_notif_service_due_sub
import com.hopcape.odo.feature.profile.resources.pf_notif_system
import com.hopcape.odo.feature.profile.resources.pf_notifications
import org.jetbrains.compose.resources.stringResource

/**
 * Notification-settings full screen ([com.hopcape.odo.core.navigation.OdoDestination.Profile.Notifications]).
 * UI-only: each toggle holds its own state (persistence lands with the ViewModel).
 */
@Composable
internal fun NotificationsScreen(onBack: () -> Unit, onDeviceSettings: () -> Unit) {
    var docExpiry by remember { mutableStateOf(true) }
    var serviceDue by remember { mutableStateOf(true) }
    var custom by remember { mutableStateOf(false) }
    var overcharge by remember { mutableStateOf(true) }
    var monthly by remember { mutableStateOf(true) }
    var health by remember { mutableStateOf(false) }
    var push by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf(false) }

    OdoScreen(title = stringResource(Res.string.pf_notifications), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            SectionLabel(stringResource(Res.string.pf_notif_reminders))
            SettingsGroup {
                OdoSwitchRow(stringResource(Res.string.pf_notif_doc_expiry), docExpiry, { docExpiry = it }, supporting = stringResource(Res.string.pf_notif_doc_expiry_sub))
                RowDivider()
                OdoSwitchRow(stringResource(Res.string.pf_notif_service_due), serviceDue, { serviceDue = it }, supporting = stringResource(Res.string.pf_notif_service_due_sub))
                RowDivider()
                OdoSwitchRow(stringResource(Res.string.pf_notif_custom), custom, { custom = it }, supporting = stringResource(Res.string.pf_notif_custom_sub))
            }

            SectionLabel(stringResource(Res.string.pf_notif_insights))
            SettingsGroup {
                OdoSwitchRow(stringResource(Res.string.pf_notif_overcharge), overcharge, { overcharge = it }, supporting = stringResource(Res.string.pf_notif_overcharge_sub))
                RowDivider()
                OdoSwitchRow(stringResource(Res.string.pf_notif_monthly), monthly, { monthly = it }, supporting = stringResource(Res.string.pf_notif_monthly_sub))
                RowDivider()
                OdoSwitchRow(stringResource(Res.string.pf_notif_health), health, { health = it }, supporting = stringResource(Res.string.pf_notif_health_sub))
            }

            SectionLabel(stringResource(Res.string.pf_notif_channels))
            SettingsGroup {
                ChannelRow(IcWindow, stringResource(Res.string.pf_notif_push), push) { push = it }
                RowDivider()
                ChannelRow(IcEnvelope, stringResource(Res.string.pf_notif_email), email) { email = it }
            }

            OdoCard(color = OdoTheme.colors.surfaceRaised) {
                Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    OdoIcon(IcInfo, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.small)
                    OdoText(stringResource(Res.string.pf_notif_system), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
                    OdoText(
                        stringResource(Res.string.pf_notif_device),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.accent,
                        modifier = Modifier.clickable(onClick = onDeviceSettings),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(icon: ImageVector, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = OdoTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIcon(icon, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.medium)
        OdoText(label, style = OdoTheme.typography.heading, modifier = Modifier.weight(1f))
        OdoSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
