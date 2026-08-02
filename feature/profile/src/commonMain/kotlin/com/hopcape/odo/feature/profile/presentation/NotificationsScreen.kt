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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoSwitch
import com.hopcape.odo.core.designsystem.component.OdoSwitchRow
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcInfo
import com.hopcape.odo.core.designsystem.icons.IcWindow
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_cd_back
import com.hopcape.odo.feature.profile.resources.pf_notif_channels
import com.hopcape.odo.feature.profile.resources.pf_notif_custom
import com.hopcape.odo.feature.profile.resources.pf_notif_custom_sub
import com.hopcape.odo.feature.profile.resources.pf_notif_device
import com.hopcape.odo.feature.profile.resources.pf_notif_doc_expiry
import com.hopcape.odo.feature.profile.resources.pf_notif_doc_expiry_sub
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
import com.hopcape.odo.feature.profile.resources.pf_notif_system_off
import com.hopcape.odo.feature.profile.resources.pf_notifications
import org.jetbrains.compose.resources.stringResource

/**
 * Notification settings. Every switch writes as it is moved — there is no Save button, so a
 * choice that only lived on screen would be lost on the way back.
 *
 * The card at the bottom reports the OS permission, which outranks everything above it: a
 * topic switched on delivers nothing while the system has notifications blocked.
 */
@Composable
internal fun NotificationsScreen(
    state: NotificationsUiState,
    onEvent: (NotificationsEvent) -> Unit,
    systemNotificationsEnabled: Boolean,
    onBack: () -> Unit,
    onDeviceSettings: () -> Unit,
) {
    val preferences = state.preferences
    OdoScreen(
        title = stringResource(Res.string.pf_notifications),
        onBack = onBack,
        backContentDescription = stringResource(Res.string.pf_cd_back),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            state.error?.let { message ->
                OdoText(
                    message.asString(),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.danger,
                )
            }

            SectionLabel(stringResource(Res.string.pf_notif_reminders))
            SettingsGroup {
                OdoSwitchRow(
                    stringResource(Res.string.pf_notif_doc_expiry),
                    preferences.documentExpiry,
                    { onEvent(NotificationsEvent.DocumentExpiryToggled(it)) },
                    supporting = stringResource(Res.string.pf_notif_doc_expiry_sub),
                )
                RowDivider()
                OdoSwitchRow(
                    stringResource(Res.string.pf_notif_service_due),
                    preferences.serviceDue,
                    { onEvent(NotificationsEvent.ServiceDueToggled(it)) },
                    supporting = stringResource(Res.string.pf_notif_service_due_sub),
                )
                RowDivider()
                OdoSwitchRow(
                    stringResource(Res.string.pf_notif_custom),
                    preferences.customReminders,
                    { onEvent(NotificationsEvent.CustomRemindersToggled(it)) },
                    supporting = stringResource(Res.string.pf_notif_custom_sub),
                )
            }

            SectionLabel(stringResource(Res.string.pf_notif_insights))
            SettingsGroup {
                OdoSwitchRow(
                    stringResource(Res.string.pf_notif_overcharge),
                    preferences.overchargeAlerts,
                    { onEvent(NotificationsEvent.OverchargeAlertsToggled(it)) },
                    supporting = stringResource(Res.string.pf_notif_overcharge_sub),
                )
                RowDivider()
                OdoSwitchRow(
                    stringResource(Res.string.pf_notif_monthly),
                    preferences.monthlySummary,
                    { onEvent(NotificationsEvent.MonthlySummaryToggled(it)) },
                    supporting = stringResource(Res.string.pf_notif_monthly_sub),
                )
                RowDivider()
                OdoSwitchRow(
                    stringResource(Res.string.pf_notif_health),
                    preferences.healthScoreDrops,
                    { onEvent(NotificationsEvent.HealthScoreDropsToggled(it)) },
                    supporting = stringResource(Res.string.pf_notif_health_sub),
                )
            }

            SectionLabel(stringResource(Res.string.pf_notif_channels))
            SettingsGroup {
                ChannelRow(IcWindow, stringResource(Res.string.pf_notif_push), preferences.push) {
                    onEvent(NotificationsEvent.PushToggled(it))
                }
            }

            OdoCard(color = OdoTheme.colors.surfaceRaised) {
                Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    OdoIcon(
                        IcInfo,
                        contentDescription = null,
                        tint = if (systemNotificationsEnabled) OdoTheme.colors.textDim else OdoTheme.colors.warning,
                        size = OdoTheme.iconSizes.small,
                    )
                    OdoText(
                        if (systemNotificationsEnabled) {
                            stringResource(Res.string.pf_notif_system)
                        } else {
                            stringResource(Res.string.pf_notif_system_off)
                        },
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textDim,
                        modifier = Modifier.weight(1f),
                    )
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
