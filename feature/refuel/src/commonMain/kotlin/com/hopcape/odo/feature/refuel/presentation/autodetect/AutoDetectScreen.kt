package com.hopcape.odo.feature.refuel.presentation.autodetect

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
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoListItem
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoSwitchRow
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.refuel.DetectionApp
import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.platform.permission.isGranted
import com.hopcape.odo.feature.refuel.resources.Res
import com.hopcape.odo.feature.refuel.resources.rf_autostart_action
import com.hopcape.odo.feature.refuel.resources.rf_autostart_body
import com.hopcape.odo.feature.refuel.resources.rf_autostart_heading
import com.hopcape.odo.feature.refuel.resources.rf_autostart_intro
import com.hopcape.odo.feature.refuel.resources.rf_autostart_skip
import com.hopcape.odo.feature.refuel.resources.rf_autostart_done
import com.hopcape.odo.feature.refuel.resources.rf_autostart_title
import com.hopcape.odo.feature.refuel.resources.rf_autodetect_title
import com.hopcape.odo.feature.refuel.resources.rf_optin_body
import com.hopcape.odo.feature.refuel.resources.rf_optin_continue
import com.hopcape.odo.feature.refuel.resources.rf_optin_cta
import com.hopcape.odo.feature.refuel.resources.rf_optin_never_others
import com.hopcape.odo.feature.refuel.resources.rf_optin_never_payment
import com.hopcape.odo.feature.refuel.resources.rf_optin_not_now
import com.hopcape.odo.feature.refuel.resources.rf_optin_on_device
import com.hopcape.odo.feature.refuel.resources.rf_optin_reads_amount
import com.hopcape.odo.feature.refuel.resources.rf_optin_reads_fuel_only
import com.hopcape.odo.feature.refuel.resources.rf_optin_title
import com.hopcape.odo.feature.refuel.resources.rf_settings_apps
import com.hopcape.odo.feature.refuel.resources.rf_settings_behaviour
import com.hopcape.odo.feature.refuel.resources.rf_settings_confirm
import com.hopcape.odo.feature.refuel.resources.rf_settings_confirm_sub
import com.hopcape.odo.feature.refuel.resources.rf_settings_detected_count
import com.hopcape.odo.feature.refuel.resources.rf_settings_detected_value
import com.hopcape.odo.feature.refuel.resources.rf_settings_ignored
import com.hopcape.odo.feature.refuel.resources.rf_settings_ignored_undo
import com.hopcape.odo.feature.refuel.resources.rf_settings_keeps_fills
import com.hopcape.odo.feature.refuel.resources.rf_settings_master
import com.hopcape.odo.feature.refuel.resources.rf_settings_master_sub
import com.hopcape.odo.feature.refuel.resources.rf_settings_notify_missing
import com.hopcape.odo.feature.refuel.resources.rf_settings_permission_missing
import com.hopcape.odo.feature.refuel.resources.rf_settings_permission_open
import com.hopcape.odo.feature.refuel.resources.rf_settings_predict
import com.hopcape.odo.feature.refuel.resources.rf_warning_body
import com.hopcape.odo.feature.refuel.resources.rf_warning_how
import com.hopcape.odo.feature.refuel.resources.rf_warning_scope
import com.hopcape.odo.feature.refuel.resources.rf_warning_sms
import com.hopcape.odo.feature.refuel.resources.rf_warning_title
import com.hopcape.odo.feature.refuel.resources.rf_optin_cta_access
import com.hopcape.odo.feature.refuel.resources.rf_optin_cta_notify
import com.hopcape.odo.feature.refuel.resources.rf_optin_cta_notify_blocked
import com.hopcape.odo.feature.refuel.resources.rf_setup_heading
import com.hopcape.odo.feature.refuel.resources.rf_setup_intro
import com.hopcape.odo.feature.refuel.resources.rf_setup_label
import com.hopcape.odo.feature.refuel.resources.rf_setup_notify_blocked
import com.hopcape.odo.feature.refuel.resources.rf_setup_notify_body
import com.hopcape.odo.feature.refuel.resources.rf_setup_notify_title
import com.hopcape.odo.feature.refuel.resources.rf_setup_read_body
import com.hopcape.odo.feature.refuel.resources.rf_setup_read_title
import com.hopcape.odo.feature.refuel.resources.rf_setup_state_done
import com.hopcape.odo.feature.refuel.resources.rf_setup_state_pending
import org.jetbrains.compose.resources.stringResource

/**
 * The auto-detect opt-in, and the settings it becomes once it is on.
 *
 * The opt-in has to earn a permission whose own system dialog says Odo will be able to read
 * all notifications, including message text. The only fair answer to that is to say first,
 * in Odo's own words, what it will actually read and what it will not — and to say that
 * nothing leaves the phone, because that is the part the system dialog cannot tell anyone.
 */
@Composable
internal fun AutoDetectScreen(
    state: AutoDetectUiState,
    onEvent: (AutoDetectEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.rf_autodetect_title),
        // On the second opt-in page, back is a step back through the explanation rather than
        // out of it. Leaving the screen from there would make the owner re-read page one to
        // reach the button they were already looking at.
        onBack = if (!state.optedIn && state.optInPage != AutoDetectOptInPage.Why) {
            { onEvent(AutoDetectEvent.OptInBacked) }
        } else {
            onBack
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OdoTheme.spacing.lg, vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            when {
                state.optedIn -> SettingsBody(state, onEvent)
                state.optInPage == AutoDetectOptInPage.Why -> WhyBody(onEvent)
                state.optInPage == AutoDetectOptInPage.Permissions -> PermissionsBody(state, onEvent)
                else -> AutostartBody(state, onEvent)
            }
        }
    }
}

/**
 * Page one: what detection does, and what it will never touch.
 *
 * No permission is named here on purpose. This page has one job — letting the owner decide
 * whether they want the feature at all — and a list of Android switches on the same screen
 * turns that decision into a form to get through.
 */
@Composable
private fun WhyBody(onEvent: (AutoDetectEvent) -> Unit) {
    OdoText(stringResource(Res.string.rf_optin_title), style = OdoTheme.typography.title)
    OdoText(
        stringResource(Res.string.rf_optin_body),
        style = OdoTheme.typography.body,
        color = OdoTheme.colors.textDim,
    )

    OdoCard(modifier = Modifier.fillMaxWidth()) {
        Promise(stringResource(Res.string.rf_optin_reads_amount), kept = true)
        Promise(stringResource(Res.string.rf_optin_reads_fuel_only), kept = true)
        Promise(stringResource(Res.string.rf_optin_never_others), kept = false)
        Promise(stringResource(Res.string.rf_optin_never_payment), kept = false)
    }

    OdoText(
        stringResource(Res.string.rf_optin_on_device),
        style = OdoTheme.typography.caption,
        color = OdoTheme.colors.textMuted,
    )

    // Names the next page rather than promising the feature is on. Nothing is switched on by
    // this tap, and a button that implied otherwise would be the same lie the old one told.
    OdoButton(
        text = stringResource(Res.string.rf_optin_continue),
        onClick = { onEvent(AutoDetectEvent.OptInAdvanced) },
        modifier = Modifier.fillMaxWidth(),
    )
    OdoButton(
        text = stringResource(Res.string.rf_optin_not_now),
        onClick = { onEvent(AutoDetectEvent.NotNowTapped) },
        variant = OdoButtonVariant.Tertiary,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Page two: the two Android notification permissions, and nothing else.
 *
 * Autostart used to sit here as a third checklist row and a card. It has its own page now,
 * because it is a different kind of thing: these two Odo can ask for and then read back, while
 * autostart is an OEM setting no API can request or check. Listing an unverifiable step
 * alongside verifiable ones made the whole list read as approximate, and it padded the one
 * screen that has to be read carefully.
 */
@Composable
private fun PermissionsBody(state: AutoDetectUiState, onEvent: (AutoDetectEvent) -> Unit) {
    OdoText(stringResource(Res.string.rf_setup_heading), style = OdoTheme.typography.title)
    OdoText(
        stringResource(Res.string.rf_setup_intro),
        style = OdoTheme.typography.body,
        color = OdoTheme.colors.textDim,
    )

    SetupChecklist(state)

    // Only on the step it is about. Shown a step earlier it would be a warning about a screen
    // the owner has not been sent to yet, which is its own kind of alarming.
    if (state.setupStep == AutoDetectSetupStep.NotificationAccess) SystemWarningCard()

    OdoButton(
        text = state.primaryActionLabel(),
        onClick = { onEvent(AutoDetectEvent.SetupContinued) },
        modifier = Modifier.fillMaxWidth(),
    )
    OdoButton(
        text = stringResource(Res.string.rf_optin_not_now),
        onClick = { onEvent(AutoDetectEvent.NotNowTapped) },
        variant = OdoButtonVariant.Tertiary,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Page three: the manufacturer's autostart switch, on the phones that have one.
 *
 * Reached only after both permissions are granted, and only where [AutoDetectUiState.needsAutostart].
 * It comes *before* detection is switched on rather than after, because a feature announced as
 * working and then silenced by the OS the next time the phone reclaims the app is worse than
 * one more screen.
 *
 * Both buttons turn detection on. Nothing here can be verified — no API reports that setting —
 * so the owner is never held on this page: [Res.string.rf_autostart_skip] is an honest way past
 * it, and the same advice comes back in the settings body until they say they have dealt with
 * it. A gate on something unreadable would never clear.
 */
@Composable
private fun AutostartBody(state: AutoDetectUiState, onEvent: (AutoDetectEvent) -> Unit) {
    OdoText(stringResource(Res.string.rf_autostart_heading), style = OdoTheme.typography.title)
    OdoText(
        stringResource(Res.string.rf_autostart_intro),
        style = OdoTheme.typography.body,
        color = OdoTheme.colors.textDim,
    )

    AutostartCard(onEvent)

    OdoButton(
        text = stringResource(Res.string.rf_optin_cta),
        onClick = { onEvent(AutoDetectEvent.SetupContinued) },
        enabled = state.permissionsSettled,
        modifier = Modifier.fillMaxWidth(),
    )
    OdoButton(
        text = stringResource(Res.string.rf_autostart_skip),
        onClick = { onEvent(AutoDetectEvent.SetupContinued) },
        variant = OdoButtonVariant.Tertiary,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * What the primary button says it will do, which is what it does.
 *
 * It used to read "Turn on auto-detect" and open a system permission page instead. A button
 * that names one thing and performs another is the fastest way to lose the benefit of the
 * doubt on the exact screen that is asking for it.
 */
@Composable
private fun AutoDetectUiState.primaryActionLabel(): String = when (setupStep) {
    AutoDetectSetupStep.PostNotifications -> if (notifyBlocked) {
        stringResource(Res.string.rf_optin_cta_notify_blocked)
    } else {
        stringResource(Res.string.rf_optin_cta_notify)
    }

    AutoDetectSetupStep.NotificationAccess -> stringResource(Res.string.rf_optin_cta_access)
    AutoDetectSetupStep.Ready -> stringResource(Res.string.rf_optin_cta)
}

/**
 * The two permissions, in the order they are asked for, each with what it is for.
 *
 * Both are shown from the start rather than one at a time. "Allow notifications" is a small
 * ask and an owner who grants it expecting to be done is entitled to know, before they tap,
 * that a second and much larger one is coming. Step 2's own copy is where the difference
 * between posting a notification and reading one gets spelled out.
 */
@Composable
private fun SetupChecklist(state: AutoDetectUiState) {
    SectionLabel(stringResource(Res.string.rf_setup_label))
    OdoCard(modifier = Modifier.fillMaxWidth()) {
        SetupRow(
            title = stringResource(Res.string.rf_setup_notify_title),
            body = if (state.notifyBlocked) {
                stringResource(Res.string.rf_setup_notify_blocked)
            } else {
                stringResource(Res.string.rf_setup_notify_body)
            },
            state = when {
                state.notifyStatus.isGranted -> SetupRowState.Done
                else -> SetupRowState.Pending
            },
        )
        SetupRow(
            title = stringResource(Res.string.rf_setup_read_title),
            body = stringResource(Res.string.rf_setup_read_body),
            state = if (state.accessGranted) SetupRowState.Done else SetupRowState.Pending,
        )
    }
}

/**
 * What the OS is about to say, said first and in Odo's own words.
 *
 * Android has a single switch for notification access, so its consent screen enumerates
 * everything the permission class can cover rather than what this app does — and some OEMs
 * dress that up with a red danger sign and a risk checkbox. Nothing in the app can restyle or
 * suppress that screen; the only thing that helps is arriving at it already knowing what it
 * says and why.
 *
 * The "Read all SMS" line is called out by name because it is the one that reads as a lie
 * about Odo. It is checkable: Odo declares no SMS permission at all, so anyone who doubts
 * this can open the app's permissions and see for themselves. Pointing at the receipt is
 * worth more than asking to be believed.
 */
@Composable
private fun SystemWarningCard() {
    OdoCard(modifier = Modifier.fillMaxWidth()) {
        OdoText(stringResource(Res.string.rf_warning_title), style = OdoTheme.typography.label)
        OdoText(
            stringResource(Res.string.rf_warning_body),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
        OdoText(
            stringResource(Res.string.rf_warning_sms),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
        OdoText(
            stringResource(Res.string.rf_warning_scope),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
        OdoText(
            stringResource(Res.string.rf_warning_how),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.text,
        )
    }
}

/**
 * How a setup row reports itself.
 *
 * Two states only, because every row on this list is an Android permission Odo can read back.
 * There used to be an `Unchecked` for autostart, which no API reports — that step has its own
 * page now, and an unverifiable row no longer sits among verifiable ones pretending to be one.
 */
private enum class SetupRowState { Done, Pending }

@Composable
private fun SetupRow(title: String, body: String, state: SetupRowState) {
    OdoListItem(
        headline = title,
        supporting = body,
        trailing = {
            OdoText(
                text = when (state) {
                    SetupRowState.Done -> stringResource(Res.string.rf_setup_state_done)
                    SetupRowState.Pending -> stringResource(Res.string.rf_setup_state_pending)
                },
                style = OdoTheme.typography.caption,
                color = when (state) {
                    SetupRowState.Done -> OdoTheme.colors.success
                    SetupRowState.Pending -> OdoTheme.colors.textDim
                },
            )
        },
    )
}

@Composable
private fun SettingsBody(state: AutoDetectUiState, onEvent: (AutoDetectEvent) -> Unit) {
    // Detection would run, but its one output has nowhere to go. Shown above the access
    // warning because it is the cheaper of the two to put right.
    if (state.needsNotifyPermission) {
        OdoCard(modifier = Modifier.fillMaxWidth()) {
            OdoText(
                stringResource(Res.string.rf_settings_notify_missing),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.warning,
            )
            OdoButton(
                text = if (state.notifyBlocked) {
                    stringResource(Res.string.rf_optin_cta_notify_blocked)
                } else {
                    stringResource(Res.string.rf_optin_cta_notify)
                },
                onClick = { onEvent(AutoDetectEvent.SetupContinued) },
                variant = OdoButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // Detection is switched on in Odo but the OS is not passing anything through. Nothing
    // else on this screen would reveal that.
    if (state.needsAccess) {
        OdoCard(modifier = Modifier.fillMaxWidth()) {
            OdoText(
                stringResource(Res.string.rf_settings_permission_missing),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.warning,
            )
            OdoButton(
                text = stringResource(Res.string.rf_settings_permission_open),
                onClick = { onEvent(AutoDetectEvent.OpenAccessSettings) },
                variant = OdoButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    OdoCard(modifier = Modifier.fillMaxWidth()) {
        SwitchRow(
            title = stringResource(Res.string.rf_settings_master),
            subtitle = stringResource(Res.string.rf_settings_master_sub),
            checked = state.optedIn,
            onChange = { onEvent(AutoDetectEvent.DetectionToggled(it)) },
        )
    }

    // Shown even when detection is on and access is granted: this is the setting that decides
    // whether it keeps working after the phone next closes the app.
    if (state.showAutostart) AutostartCard(onEvent)

    if (state.apps.isNotEmpty()) {
        SectionLabel(stringResource(Res.string.rf_settings_apps))
        OdoCard(modifier = Modifier.fillMaxWidth()) {
            state.apps.forEach { app ->
                SwitchRow(
                    title = app.displayName(),
                    subtitle = null,
                    checked = app.enabled,
                    onChange = { onEvent(AutoDetectEvent.AppToggled(app.packageName, it)) },
                )
            }
        }
    }

    SectionLabel(stringResource(Res.string.rf_settings_behaviour))
    OdoCard(modifier = Modifier.fillMaxWidth()) {
        SwitchRow(
            title = stringResource(Res.string.rf_settings_confirm),
            subtitle = stringResource(Res.string.rf_settings_confirm_sub),
            checked = state.confirmBeforeLog,
            onChange = { onEvent(AutoDetectEvent.ConfirmBeforeLogToggled(it)) },
        )
        SwitchRow(
            title = stringResource(Res.string.rf_settings_predict),
            subtitle = null,
            checked = state.predictOdometer,
            onChange = { onEvent(AutoDetectEvent.PredictOdometerToggled(it)) },
        )
        OdoListItem(
            headline = stringResource(Res.string.rf_settings_detected_count),
            trailing = {
                OdoText(
                    stringResource(Res.string.rf_settings_detected_value, state.detectedFillCount),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            },
        )
    }
    OdoText(
        stringResource(Res.string.rf_settings_keeps_fills),
        style = OdoTheme.typography.caption,
        color = OdoTheme.colors.textMuted,
    )

    // Only shown once there is something in it: an empty "merchants you rejected" list is a
    // heading explaining a feature nobody has used.
    if (state.ignoredMerchants.isNotEmpty()) {
        SectionLabel(stringResource(Res.string.rf_settings_ignored))
        OdoCard(modifier = Modifier.fillMaxWidth()) {
            state.ignoredMerchants.forEach { merchant ->
                OdoListItem(
                    headline = merchant.label,
                    trailing = {
                        OdoButton(
                            text = stringResource(Res.string.rf_settings_ignored_undo),
                            onClick = { onEvent(AutoDetectEvent.MerchantUnignored(merchant.key)) },
                            variant = OdoButtonVariant.Tertiary,
                        )
                    },
                )
            }
        }
    }
}

/**
 * The one thing on this screen the app cannot do anything about.
 *
 * On these phones a separate autostart switch decides whether Odo may be started in the
 * background at all. While it is off the system refuses to start the app, the listener never
 * binds, and detection stops the moment the owner closes Odo — with every switch on this
 * screen still reading "on". Saying so plainly is the only honest option; the button just
 * shortens the walk.
 */
@Composable
private fun AutostartCard(onEvent: (AutoDetectEvent) -> Unit) {
    OdoCard(modifier = Modifier.fillMaxWidth()) {
        OdoText(stringResource(Res.string.rf_autostart_title), style = OdoTheme.typography.label)
        OdoText(
            stringResource(Res.string.rf_autostart_body),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
        OdoButton(
            text = stringResource(Res.string.rf_autostart_action),
            onClick = { onEvent(AutoDetectEvent.OpenAutostartSettings) },
            variant = OdoButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        // The only way this card can ever go away. Nothing can read the setting it is about,
        // so the owner saying "done" is the signal — and advice with no way out is advice
        // that trains people to ignore the screen it is on.
        OdoButton(
            text = stringResource(Res.string.rf_autostart_done),
            onClick = { onEvent(AutoDetectEvent.AutostartAcknowledged) },
            variant = OdoButtonVariant.Tertiary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    OdoText(text, style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    OdoSwitchRow(
        label = title,
        supporting = subtitle,
        checked = checked,
        onCheckedChange = onChange,
    )
}

/** One line of the opt-in's promise list. [kept] separates what Odo does from what it never does. */
@Composable
private fun Promise(text: String, kept: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoText(
            text = if (kept) "+" else "—",
            style = OdoTheme.typography.label,
            color = if (kept) OdoTheme.colors.success else OdoTheme.colors.textMuted,
        )
        OdoText(
            text = text,
            style = OdoTheme.typography.bodySmall,
            color = if (kept) OdoTheme.colors.text else OdoTheme.colors.textDim,
        )
    }
}

/**
 * The app's name as the owner would recognise it.
 *
 * A package name is what the store holds, because that is what the listener matches on, and
 * "com.google.android.apps.nbu.paisa.user" is not a thing to put in front of anyone.
 */
private fun DetectionApp.displayName(): String = when (packageName) {
    "com.google.android.apps.nbu.paisa.user" -> "Google Pay"
    "com.phonepe.app" -> "PhonePe"
    "net.one97.paytm" -> "Paytm"
    else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}

/** Page one — the pitch and the promises, with no permission named. */
@OdoThemePreviews
@Composable
private fun AutoDetectOptInPreview() = OdoPreview {
    AutoDetectScreen(state = AutoDetectUiState(loading = false), onEvent = {}, onBack = {})
}

/** Page two, mid-chain on a phone with an autostart switch — where the OEM warning shows. */
@OdoThemePreviews
@Composable
private fun AutoDetectOptInNeedsAccessPreview() = OdoPreview {
    AutoDetectScreen(
        state = AutoDetectUiState(
            loading = false,
            optInPage = AutoDetectOptInPage.Permissions,
            notifyStatus = PermissionStatus.Granted,
            needsAutostart = true,
        ),
        onEvent = {},
        onBack = {},
    )
}

@OdoThemePreviews
@Composable
private fun AutoDetectSettingsPreview() = OdoPreview {
    AutoDetectScreen(
        state = AutoDetectUiState(
            loading = false,
            optedIn = true,
            accessGranted = true,
            apps = listOf(
                DetectionApp("com.google.android.apps.nbu.paisa.user", enabled = true),
                DetectionApp("com.phonepe.app", enabled = true),
            ),
            detectedFillCount = 14,
        ),
        onEvent = {},
        onBack = {},
    )
}
