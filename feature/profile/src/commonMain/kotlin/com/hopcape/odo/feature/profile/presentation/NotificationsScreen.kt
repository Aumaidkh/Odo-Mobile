package com.hopcape.odo.feature.profile.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoChip
import com.hopcape.odo.core.designsystem.component.OdoDropdownField
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoSwitch
import com.hopcape.odo.core.designsystem.component.OdoSwitchRow
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.common.FeatureFlags
import com.hopcape.odo.core.designsystem.icons.IcFuelPump
import com.hopcape.odo.core.designsystem.icons.IcInfo
import com.hopcape.odo.core.designsystem.icons.IcWindow
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.policy.DocumentReminderPolicy
import com.hopcape.odo.core.domain.settings.model.NotificationSchedule
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_cd_back
import com.hopcape.odo.feature.profile.resources.pf_notif_auto_detect
import com.hopcape.odo.feature.profile.resources.pf_notif_auto_detect_sub
import com.hopcape.odo.feature.profile.resources.pf_notif_channels
import com.hopcape.odo.feature.profile.resources.pf_notif_detection
import com.hopcape.odo.feature.profile.resources.pf_notif_custom
import com.hopcape.odo.feature.profile.resources.pf_notif_custom_sub
import com.hopcape.odo.feature.profile.resources.pf_notif_device
import com.hopcape.odo.feature.profile.resources.pf_notif_doc_expiry
import com.hopcape.odo.feature.profile.resources.pf_doc_kind_insurance
import com.hopcape.odo.feature.profile.resources.pf_doc_kind_licence
import com.hopcape.odo.feature.profile.resources.pf_doc_kind_puc
import com.hopcape.odo.feature.profile.resources.pf_notif_doc_expiry_sub
import com.hopcape.odo.feature.profile.resources.pf_notif_hour_am
import com.hopcape.odo.feature.profile.resources.pf_notif_hour_noon
import com.hopcape.odo.feature.profile.resources.pf_notif_hour_pm
import com.hopcape.odo.feature.profile.resources.pf_notif_lead_day
import com.hopcape.odo.feature.profile.resources.pf_notif_lead_days
import com.hopcape.odo.feature.profile.resources.pf_notif_lead_none
import com.hopcape.odo.feature.profile.resources.pf_notif_leads_note
import com.hopcape.odo.feature.profile.resources.pf_notif_leads_title
import com.hopcape.odo.feature.profile.resources.pf_notif_time
import com.hopcape.odo.feature.profile.resources.pf_notif_time_note
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
    onAutoDetect: () -> Unit,
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
                // Only under an expiry switch that is on: leads for a topic that is off are
                // a choice about nothing, and the row would read as a promise Odo is not
                // keeping.
                if (preferences.documentExpiry) {
                    RowDivider()
                    DocumentLeads(schedule = state.schedule, onEvent = onEvent)
                }
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
                RowDivider()
                NotifyAtRow(hour = state.schedule.notifyAtHour) {
                    onEvent(NotificationsEvent.NotifyHourChosen(it))
                }
            }

            // The one notification setting that is about *reading* rather than posting, which
            // is why it opens a screen of its own instead of being a switch here: it needs a
            // permission, and a permission is not something a toggle in a list can explain.
            // Absent entirely while SMART_REFUEL_DETECT_ENABLED is false — a row that leads to
            // a feature the build cannot run is worse than no row.
            if (FeatureFlags.SMART_REFUEL_DETECT_ENABLED) {
                SectionLabel(stringResource(Res.string.pf_notif_detection))
                SettingsGroup {
                    SettingsRow(
                        icon = IcFuelPump,
                        title = stringResource(Res.string.pf_notif_auto_detect),
                        onClick = onAutoDetect,
                    )
                }
                OdoText(
                    stringResource(Res.string.pf_notif_auto_detect_sub),
                    style = OdoTheme.typography.caption,
                    color = OdoTheme.colors.textMuted,
                    modifier = Modifier.padding(horizontal = OdoTheme.spacing.xs),
                )
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

/**
 * The lead chips, one row of them per kind of paper Odo chases.
 *
 * Several may be on at once, because that is how the reminders already work: an insurance
 * policy is worth a nudge a month out *and* the day before, and forcing one would drop the
 * staged warnings the product was built around. Turning them all off is a legitimate answer —
 * "track this paper, do not nag me about it" — and the row says so rather than looking broken.
 */
@Composable
private fun DocumentLeads(schedule: NotificationSchedule, onEvent: (NotificationsEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = OdoTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        OdoText(
            stringResource(Res.string.pf_notif_leads_title),
            style = OdoTheme.typography.label,
            color = OdoTheme.colors.textDim,
        )
        DocumentReminderPolicy.chasedTypes.forEach { type ->
            val chosen = schedule.leadDaysFor(type)
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(documentTypeLabel(type), style = OdoTheme.typography.body)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
                ) {
                    DocumentReminderPolicy.LEAD_DAY_PRESETS.forEach { days ->
                        val selected = days in chosen
                        OdoChip(
                            label = leadLabel(days),
                            selected = selected,
                            onClick = {
                                onEvent(
                                    NotificationsEvent.DocumentLeadToggled(
                                        type = type,
                                        days = days,
                                        selected = !selected,
                                    ),
                                )
                            },
                            modifier = Modifier.testTag(NotificationsTestTags.leadChip(type, days)),
                        )
                    }
                }
                if (chosen.isEmpty()) {
                    OdoText(
                        stringResource(Res.string.pf_notif_lead_none),
                        style = OdoTheme.typography.caption,
                        color = OdoTheme.colors.textDim,
                    )
                }
            }
        }
        OdoText(
            stringResource(Res.string.pf_notif_leads_note),
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textDim,
        )
    }
}

/** The one time of day every reminder arrives at. */
@Composable
private fun NotifyAtRow(hour: Int, onHour: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = OdoTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
    ) {
        // Resolved once, here: the labels are string resources, and the field hands back the
        // one that was picked rather than the hour behind it.
        val hours = NotificationSchedule.SELECTABLE_HOURS.associateWith { hourLabel(it) }
        OdoDropdownField(
            selected = hours[hour] ?: hourLabel(hour),
            options = hours.values.toList(),
            onSelect = { label -> hours.entries.firstOrNull { it.value == label }?.key?.let(onHour) },
            label = stringResource(Res.string.pf_notif_time),
            modifier = Modifier.testTag(NotificationsTestTags.NOTIFY_AT_FIELD),
        )
        OdoText(
            stringResource(Res.string.pf_notif_time_note),
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textDim,
        )
    }
}

/** "30 days" / "1 day" — the chip's own label. */
@Composable
private fun leadLabel(days: Int): String = when (days) {
    1 -> stringResource(Res.string.pf_notif_lead_day, days)
    else -> stringResource(Res.string.pf_notif_lead_days, days)
}

/** "8 AM" / "12 PM" / "9 PM", from an hour of the 24-hour clock. */
@Composable
private fun hourLabel(hour: Int): String = when {
    hour == 12 -> stringResource(Res.string.pf_notif_hour_noon)
    hour > 12 -> stringResource(Res.string.pf_notif_hour_pm, hour - 12)
    else -> stringResource(Res.string.pf_notif_hour_am, hour)
}

/** The paper's name, as the notifications screen says it. */
@Composable
private fun documentTypeLabel(type: DocumentType): String = when (type) {
    DocumentType.PUC -> stringResource(Res.string.pf_doc_kind_puc)
    DocumentType.LICENCE -> stringResource(Res.string.pf_doc_kind_licence)
    else -> stringResource(Res.string.pf_doc_kind_insurance)
}

/**
 * Tags for the controls that share their words with each other — every kind of paper offers
 * the same "30 days" chip, and only the row it sits in tells them apart. Public like the
 * other screens' tags, so `:androidApp`'s instrumented suite can reach them.
 */
object NotificationsTestTags {
    const val NOTIFY_AT_FIELD: String = "notif_notify_at"

    fun leadChip(type: DocumentType, days: Int): String = "notif_lead_${type.name}_$days"
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
