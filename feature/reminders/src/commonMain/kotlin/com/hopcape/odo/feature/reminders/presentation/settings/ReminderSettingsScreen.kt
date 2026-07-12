package com.hopcape.odo.feature.reminders.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoChip
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcBell
import com.hopcape.odo.core.designsystem.icons.IcChat
import com.hopcape.odo.core.designsystem.icons.IcEnvelope
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.reminders.resources.Res
import com.hopcape.odo.feature.reminders.resources.rm_settings_before
import com.hopcape.odo.feature.reminders.resources.rm_settings_before_14
import com.hopcape.odo.feature.reminders.resources.rm_settings_before_3
import com.hopcape.odo.feature.reminders.resources.rm_settings_before_30
import com.hopcape.odo.feature.reminders.resources.rm_settings_before_7
import com.hopcape.odo.feature.reminders.resources.rm_settings_email
import com.hopcape.odo.feature.reminders.resources.rm_settings_how
import com.hopcape.odo.feature.reminders.resources.rm_settings_insurance
import com.hopcape.odo.feature.reminders.resources.rm_settings_partner
import com.hopcape.odo.feature.reminders.resources.rm_settings_partner_sub
import com.hopcape.odo.feature.reminders.resources.rm_settings_push
import com.hopcape.odo.feature.reminders.resources.rm_settings_service
import com.hopcape.odo.feature.reminders.resources.rm_settings_title
import com.hopcape.odo.feature.reminders.resources.rm_settings_tyre
import com.hopcape.odo.feature.reminders.resources.rm_settings_what
import com.hopcape.odo.feature.reminders.resources.rm_settings_whatsapp
import org.jetbrains.compose.resources.stringResource

/**
 * Reminder settings — the notification channels ("how to notify me"), the topics to be
 * reminded about ("what to remind me about"), and the lead time ("remind me before").
 * Reached from the reminders home's "Manage".
 *
 * State-free: renders [state] and forwards toggle / lead-time intents.
 */
@Composable
internal fun ReminderSettingsScreen(
    state: ReminderSettingsUiState,
    onToggle: (ReminderToggle) -> Unit,
    onRemindBeforeChange: (RemindBefore) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(modifier = modifier, title = stringResource(Res.string.rm_settings_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            Section(stringResource(Res.string.rm_settings_how)) {
                GroupCard {
                    ToggleRow(IcBell, OdoTheme.colors.text, stringResource(Res.string.rm_settings_push), null, state.push) { onToggle(ReminderToggle.PUSH) }
                    RowDivider()
                    ToggleRow(IcChat, OdoTheme.colors.success, stringResource(Res.string.rm_settings_whatsapp), null, state.whatsapp) { onToggle(ReminderToggle.WHATSAPP) }
                    RowDivider()
                    ToggleRow(IcEnvelope, OdoTheme.colors.text, stringResource(Res.string.rm_settings_email), null, state.email) { onToggle(ReminderToggle.EMAIL) }
                }
            }

            Section(stringResource(Res.string.rm_settings_what)) {
                GroupCard {
                    ToggleRow(null, Color.Unspecified, stringResource(Res.string.rm_settings_insurance), null, state.insurance) { onToggle(ReminderToggle.INSURANCE) }
                    RowDivider()
                    ToggleRow(null, Color.Unspecified, stringResource(Res.string.rm_settings_service), null, state.service) { onToggle(ReminderToggle.SERVICE) }
                    RowDivider()
                    ToggleRow(null, Color.Unspecified, stringResource(Res.string.rm_settings_tyre), null, state.tyre) { onToggle(ReminderToggle.TYRE) }
                    RowDivider()
                    ToggleRow(null, Color.Unspecified, stringResource(Res.string.rm_settings_partner), stringResource(Res.string.rm_settings_partner_sub), state.partner) { onToggle(ReminderToggle.PARTNER) }
                }
            }

            Section(stringResource(Res.string.rm_settings_before)) {
                Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                    RemindBefore.entries.forEach { option ->
                        OdoChip(label = leadTimeLabel(option), selected = option == state.remindBefore, onClick = { onRemindBeforeChange(option) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        OdoText(label, style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
        content()
    }
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    OdoCard(
        contentPadding = PaddingValues(horizontal = OdoTheme.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        content()
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(color = OdoTheme.colors.border)

@Composable
private fun ToggleRow(
    icon: ImageVector?,
    iconTint: Color,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            OdoIcon(icon, contentDescription = null, tint = iconTint, size = OdoTheme.iconSizes.medium)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(title, style = OdoTheme.typography.heading)
            if (subtitle != null) OdoText(subtitle, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        }
        SettingSwitch(checked = checked, onToggle = onToggle)
    }
}

@Composable
private fun SettingSwitch(checked: Boolean, onToggle: () -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = { onToggle() },
        colors = SwitchDefaults.colors(
            checkedThumbColor = OdoTheme.colors.onAccent,
            checkedTrackColor = OdoTheme.colors.accent,
            checkedBorderColor = OdoTheme.colors.accent,
            uncheckedThumbColor = OdoTheme.colors.textDim,
            uncheckedTrackColor = OdoTheme.colors.surfaceRaised,
            uncheckedBorderColor = OdoTheme.colors.border,
        ),
    )
}

@Composable
private fun leadTimeLabel(option: RemindBefore): String = stringResource(
    when (option) {
        RemindBefore.D3 -> Res.string.rm_settings_before_3
        RemindBefore.D7 -> Res.string.rm_settings_before_7
        RemindBefore.D14 -> Res.string.rm_settings_before_14
        RemindBefore.D30 -> Res.string.rm_settings_before_30
    },
)

@OdoThemePreviews
@Composable
private fun ReminderSettingsScreenPreview() = OdoPreview(padded = false) {
    ReminderSettingsScreen(sampleReminderSettings(), onToggle = {}, onRemindBeforeChange = {}, onBack = {})
}
