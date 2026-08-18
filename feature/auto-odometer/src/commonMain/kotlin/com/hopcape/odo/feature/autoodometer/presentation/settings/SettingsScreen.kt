package com.hopcape.odo.feature.autoodometer.presentation.settings

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
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoConfirmDialog
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoPermissionNudge
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoSwitchRow
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCar
import com.hopcape.odo.core.designsystem.icons.IcTrash
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.core.domain.shared.formatDayMonth
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.feature.autoodometer.resources.Res
import com.hopcape.odo.feature.autoodometer.resources.ao_cd_back
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_activity_title
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_location_title
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_open_settings
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_all_auto_logged
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_change
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_delete_all
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_delete_confirm_body
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_delete_confirm_cancel
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_delete_confirm_cta
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_delete_confirm_title
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_fix_it_background_location
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_fix_it_bluetooth
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_autostart_body
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_autostart_cta
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_autostart_title
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_fix_it_row
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_footer
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_logs_to
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_logs_to_no_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_logs_to_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_no_manual_entries
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_paused_until
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_pause_week
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_resume
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_section_privacy
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_section_tracked_month
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_section_trigger_device
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_title
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_tracking_off
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_tracking_on
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_trip_in_progress
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_trips_count
import com.hopcape.odo.feature.autoodometer.resources.ao_settings_waiting_stereo
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Tracking toggle, trigger device, monthly stats and privacy controls (M7). No Keep-routes row
 * — only distance is ever stored, so that toggle's off-state is the only state (plan D2).
 *
 * State-free: renders [state] and forwards intents. The route host owns every readiness
 * [com.hopcape.odo.core.platform.permission.PermissionController] and decides what
 * [onFixIt] actually triggers for a given [ReadinessIssue], the same split every other screen
 * in this feature uses for its permission controllers.
 */
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onToggle: () -> Unit,
    onResume: () -> Unit,
    onChangeDevice: () -> Unit,
    onPauseWeek: () -> Unit,
    onDelete: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onFixIt: (ReadinessIssue) -> Unit,
    onAutostartAdvice: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.ao_settings_title),
        onBack = onBack,
        backContentDescription = stringResource(Res.string.ao_cd_back),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            if (state.isPaused) {
                PausedCard(state, onResume)
            } else {
                ToggleCard(state, onToggle)
            }

            if (state.readinessIssues.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                    state.readinessIssues.forEach { issue ->
                        FixItRow(issue = issue, onAction = { onFixIt(issue) })
                    }
                }
            }

            if (state.showAutostartAdvice) {
                AutostartAdviceCard(onAutostartAdvice)
            }

            if (state.hasBond) {
                Section(stringResource(Res.string.ao_settings_section_trigger_device)) {
                    TriggerDeviceCard(state.mode, onChangeDevice)
                }
            }

            Section(stringResource(Res.string.ao_settings_section_tracked_month)) {
                MonthlyStatsCard(state)
            }

            Section(stringResource(Res.string.ao_settings_section_privacy)) {
                PrivacyCard(deleting = state.deleting, onPauseWeek = onPauseWeek, onDelete = onDelete)
            }

            OdoText(
                stringResource(Res.string.ao_settings_footer),
                style = OdoTheme.typography.caption,
                color = OdoTheme.colors.textMuted,
            )
        }
    }

    if (state.showDeleteConfirm) {
        OdoConfirmDialog(
            title = stringResource(Res.string.ao_settings_delete_confirm_title),
            body = stringResource(Res.string.ao_settings_delete_confirm_body),
            confirmLabel = stringResource(Res.string.ao_settings_delete_confirm_cta),
            cancelLabel = stringResource(Res.string.ao_settings_delete_confirm_cancel),
            onConfirm = onDeleteConfirm,
            onDismiss = onDeleteDismiss,
        )
    }
}

@Composable
private fun ToggleCard(state: SettingsUiState, onToggle: () -> Unit) {
    OdoCard {
        OdoSwitchRow(
            label = stringResource(
                if (state.trackingEnabled) Res.string.ao_settings_tracking_on else Res.string.ao_settings_tracking_off,
            ),
            checked = state.trackingEnabled,
            onCheckedChange = { onToggle() },
            supporting = state.statusDetail?.asString(),
        )
    }
}

@Composable
private fun PausedCard(state: SettingsUiState, onResume: () -> Unit) {
    OdoCard {
        val until = state.pausedUntil
        OdoText(
            if (until != null) {
                stringResource(Res.string.ao_settings_paused_until, formatDayMonth(until))
            } else {
                stringResource(Res.string.ao_settings_tracking_off)
            },
            style = OdoTheme.typography.heading,
        )
        OdoButton(
            text = stringResource(Res.string.ao_settings_resume),
            onClick = onResume,
            variant = OdoButtonVariant.Secondary,
        )
    }
}

/**
 * The autostart advice — a card rather than a [FixItRow], because a fix-it row states that a
 * named permission was turned off, and this cannot state anything. The manufacturer's switch
 * is unreadable, so the copy says what may be happening and leaves the checking to the owner.
 */
@Composable
private fun AutostartAdviceCard(onAction: () -> Unit) {
    OdoCard {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            OdoText(
                stringResource(Res.string.ao_settings_autostart_title),
                style = OdoTheme.typography.heading,
            )
            OdoText(
                stringResource(Res.string.ao_settings_autostart_body),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
            )
            OdoButton(
                text = stringResource(Res.string.ao_settings_autostart_cta),
                onClick = onAction,
                variant = OdoButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FixItRow(issue: ReadinessIssue, onAction: () -> Unit) {
    val label = stringResource(issue.labelResource())
    OdoPermissionNudge(
        icon = IcWarning,
        message = stringResource(Res.string.ao_settings_fix_it_row, label),
        actionLabel = stringResource(Res.string.ao_permissions_open_settings),
        onAction = onAction,
    )
}

private fun ReadinessIssue.labelResource(): StringResource = when (this) {
    ReadinessIssue.FINE_LOCATION -> Res.string.ao_permissions_location_title
    ReadinessIssue.BACKGROUND_LOCATION -> Res.string.ao_settings_fix_it_background_location
    ReadinessIssue.ACTIVITY_RECOGNITION -> Res.string.ao_permissions_activity_title
    ReadinessIssue.BLUETOOTH_CONNECT -> Res.string.ao_settings_fix_it_bluetooth
}

@Composable
private fun TriggerDeviceCard(mode: TriggerMode?, onChangeDevice: () -> Unit) {
    OdoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoIcon(IcCar, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.medium)
            Column(Modifier.weight(1f)) {
                OdoText(stringResource(Res.string.ao_settings_logs_to), style = OdoTheme.typography.caption, color = OdoTheme.colors.textDim)
                OdoText(stringResource(mode.logsToResource()), style = OdoTheme.typography.body)
            }
            OdoText(
                stringResource(Res.string.ao_settings_change),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.accent,
                modifier = Modifier.clickable(onClick = onChangeDevice),
            )
        }
    }
}

private fun TriggerMode?.logsToResource(): StringResource = when (this) {
    TriggerMode.STEREO -> Res.string.ao_settings_logs_to_stereo
    TriggerMode.NO_STEREO -> Res.string.ao_settings_logs_to_no_stereo
    null -> Res.string.ao_settings_logs_to_stereo
}

@Composable
private fun MonthlyStatsCard(state: SettingsUiState) {
    val distance = LocalOdoDistanceFormat.current
    OdoCard {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.Bottom) {
            OdoText(distance.format(state.monthlyDistanceKm), style = OdoTheme.typography.title)
            OdoText(
                stringResource(Res.string.ao_settings_trips_count, state.tripCount),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
        OdoText(stringResource(Res.string.ao_settings_all_auto_logged), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        OdoText(stringResource(Res.string.ao_settings_no_manual_entries), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
    }
}

@Composable
private fun PrivacyCard(deleting: Boolean, onPauseWeek: () -> Unit, onDelete: () -> Unit) {
    OdoCard(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        PrivacyActionRow(text = stringResource(Res.string.ao_settings_pause_week), onClick = onPauseWeek)
        OdoDivider()
        PrivacyActionRow(
            text = stringResource(Res.string.ao_settings_delete_all),
            onClick = onDelete,
            enabled = !deleting,
            danger = true,
        )
    }
}

@Composable
private fun PrivacyActionRow(text: String, onClick: () -> Unit, enabled: Boolean = true, danger: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = OdoTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (danger) {
            OdoIcon(IcTrash, contentDescription = null, tint = OdoTheme.colors.danger, size = OdoTheme.iconSizes.medium)
        }
        OdoText(text, style = OdoTheme.typography.body, color = if (danger) OdoTheme.colors.danger else OdoTheme.colors.text)
    }
}

@Composable
private fun Section(header: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
        OdoText(header, style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
        content()
    }
}

@OdoThemePreviews
@Composable
private fun SettingsScreenTrackingPreview() = OdoPreview(padded = false) {
    SettingsScreen(
        state = SettingsUiState(
            loading = false,
            trackingEnabled = true,
            statusDetail = UiText(Res.string.ao_settings_trip_in_progress),
            mode = TriggerMode.STEREO,
            hasBond = true,
            tripCount = 18,
            monthlyDistanceKm = 842,
        ),
        onToggle = {},
        onResume = {},
        onChangeDevice = {},
        onPauseWeek = {},
        onDelete = {},
        onDeleteConfirm = {},
        onDeleteDismiss = {},
        onFixIt = {},
        onAutostartAdvice = {},
        onBack = {},
    )
}

@OdoThemePreviews
@Composable
private fun SettingsScreenStandbyPreview() = OdoPreview(padded = false) {
    SettingsScreen(
        state = SettingsUiState(
            loading = false,
            trackingEnabled = true,
            statusDetail = UiText(Res.string.ao_settings_waiting_stereo),
            mode = TriggerMode.STEREO,
            hasBond = true,
            tripCount = 18,
            monthlyDistanceKm = 842,
        ),
        onToggle = {},
        onResume = {},
        onChangeDevice = {},
        onPauseWeek = {},
        onDelete = {},
        onDeleteConfirm = {},
        onDeleteDismiss = {},
        onFixIt = {},
        onAutostartAdvice = {},
        onBack = {},
    )
}

@OdoThemePreviews
@Composable
private fun SettingsScreenDisabledPreview() = OdoPreview(padded = false) {
    SettingsScreen(
        state = SettingsUiState(loading = false, trackingEnabled = false),
        onToggle = {},
        onResume = {},
        onChangeDevice = {},
        onPauseWeek = {},
        onDelete = {},
        onDeleteConfirm = {},
        onDeleteDismiss = {},
        onFixIt = {},
        onAutostartAdvice = {},
        onBack = {},
    )
}

@OdoThemePreviews
@Composable
private fun SettingsScreenPausedPreview() = OdoPreview(padded = false) {
    SettingsScreen(
        state = SettingsUiState(
            loading = false,
            trackingEnabled = false,
            hasBond = true,
            mode = TriggerMode.STEREO,
            tripCount = 6,
            monthlyDistanceKm = 214,
            pausedUntil = LocalDate(2026, 8, 14),
        ),
        onToggle = {},
        onResume = {},
        onChangeDevice = {},
        onPauseWeek = {},
        onDelete = {},
        onDeleteConfirm = {},
        onDeleteDismiss = {},
        onFixIt = {},
        onAutostartAdvice = {},
        onBack = {},
    )
}

@OdoThemePreviews
@Composable
private fun SettingsScreenFixItRowsPreview() = OdoPreview(padded = false) {
    SettingsScreen(
        state = SettingsUiState(
            loading = false,
            trackingEnabled = true,
            mode = TriggerMode.STEREO,
            hasBond = true,
            tripCount = 18,
            monthlyDistanceKm = 842,
            readinessIssues = listOf(ReadinessIssue.BACKGROUND_LOCATION, ReadinessIssue.BLUETOOTH_CONNECT),
        ),
        onToggle = {},
        onResume = {},
        onChangeDevice = {},
        onPauseWeek = {},
        onDelete = {},
        onDeleteConfirm = {},
        onDeleteDismiss = {},
        onFixIt = {},
        onAutostartAdvice = {},
        onBack = {},
    )
}
