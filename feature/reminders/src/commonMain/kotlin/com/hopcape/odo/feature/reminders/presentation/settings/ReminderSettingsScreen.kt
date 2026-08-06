package com.hopcape.odo.feature.reminders.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcBellOutlined
import com.hopcape.odo.core.designsystem.icons.IcChatOutlined
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.reminders.presentation.RemindersTestTags
import com.hopcape.odo.feature.reminders.presentation.state.Loadable
import com.hopcape.odo.feature.reminders.resources.Res
import com.hopcape.odo.feature.reminders.resources.rm_settings_custom
import com.hopcape.odo.feature.reminders.resources.rm_settings_how
import com.hopcape.odo.feature.reminders.resources.rm_settings_insurance
import com.hopcape.odo.feature.reminders.resources.rm_settings_partner
import com.hopcape.odo.feature.reminders.resources.rm_settings_partner_sub
import com.hopcape.odo.feature.reminders.resources.rm_settings_push
import com.hopcape.odo.feature.reminders.resources.rm_settings_service
import com.hopcape.odo.feature.reminders.resources.rm_settings_title
import com.hopcape.odo.feature.reminders.resources.rm_settings_what
import com.hopcape.odo.feature.reminders.resources.rm_settings_whatsapp
import com.hopcape.odo.feature.reminders.resources.rm_settings_whatsapp_soon
import org.jetbrains.compose.resources.stringResource

/**
 * Reminder settings — the notification channels ("how to notify me") and the topics to
 * be reminded about ("what to remind me about"). Reached from the reminders home's
 * "Manage".
 *
 * The WhatsApp row renders disabled ("coming soon"): the server knows the channel but
 * no sender exists yet, and a live switch would promise messages nobody can send. There
 * is no Email row and no global lead time — Email was cut with the profile work, and
 * lead times are per-type policy, not a preference.
 *
 * State-free: renders [state] and forwards toggle intents.
 */
@Composable
internal fun ReminderSettingsScreen(
    state: ReminderSettingsUiState,
    onToggle: (ReminderToggle) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(modifier = modifier, title = stringResource(Res.string.rm_settings_title), onBack = onBack) { padding ->
        when (val content = state.content) {
            Loadable.Loading -> Box(Modifier.fillMaxSize().padding(padding))

            is Loadable.Failed -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                OdoText(content.message.asString(), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            }

            is Loadable.Ready -> SettingsContent(content.value, padding, onToggle)
        }
    }
}

@Composable
private fun SettingsContent(
    content: ReminderSettingsContent,
    padding: PaddingValues,
    onToggle: (ReminderToggle) -> Unit,
) {
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
                ToggleRow(IcBellOutlined, OdoTheme.colors.text, stringResource(Res.string.rm_settings_push), null, content.push, tag = RemindersTestTags.settingsToggle(ReminderToggle.PUSH.name)) { onToggle(ReminderToggle.PUSH) }
                RowDivider()
                ToggleRow(
                    icon = IcChatOutlined,
                    iconTint = OdoTheme.colors.success,
                    title = stringResource(Res.string.rm_settings_whatsapp),
                    subtitle = stringResource(Res.string.rm_settings_whatsapp_soon),
                    checked = content.whatsapp,
                    enabled = false,
                    tag = RemindersTestTags.SETTINGS_WHATSAPP_SWITCH,
                    onToggle = {},
                )
            }
        }

        Section(stringResource(Res.string.rm_settings_what)) {
            GroupCard {
                ToggleRow(null, Color.Unspecified, stringResource(Res.string.rm_settings_insurance), null, content.documents, tag = RemindersTestTags.settingsToggle(ReminderToggle.DOCUMENTS.name)) { onToggle(ReminderToggle.DOCUMENTS) }
                RowDivider()
                ToggleRow(null, Color.Unspecified, stringResource(Res.string.rm_settings_service), null, content.service, tag = RemindersTestTags.settingsToggle(ReminderToggle.SERVICE.name)) { onToggle(ReminderToggle.SERVICE) }
                RowDivider()
                ToggleRow(null, Color.Unspecified, stringResource(Res.string.rm_settings_custom), null, content.custom, tag = RemindersTestTags.settingsToggle(ReminderToggle.CUSTOM.name)) { onToggle(ReminderToggle.CUSTOM) }
                RowDivider()
                ToggleRow(null, Color.Unspecified, stringResource(Res.string.rm_settings_partner), stringResource(Res.string.rm_settings_partner_sub), content.partner, tag = RemindersTestTags.settingsToggle(ReminderToggle.PARTNER.name)) { onToggle(ReminderToggle.PARTNER) }
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
    enabled: Boolean = true,
    tag: String? = null,
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
        SettingSwitch(checked = checked, enabled = enabled, tag = tag, onToggle = onToggle)
    }
}

@Composable
private fun SettingSwitch(checked: Boolean, enabled: Boolean, tag: String?, onToggle: () -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = { onToggle() },
        enabled = enabled,
        modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
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

@OdoThemePreviews
@Composable
private fun ReminderSettingsScreenPreview() = OdoPreview(padded = false) {
    ReminderSettingsScreen(sampleReminderSettings(), onToggle = {}, onBack = {})
}
