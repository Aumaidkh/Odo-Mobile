package com.hopcape.odo.feature.refuel.presentation.autodetect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoListItem
import com.hopcape.odo.core.designsystem.component.OdoPermissionAssurance
import com.hopcape.odo.core.designsystem.component.OdoPermissionAssuranceKind
import com.hopcape.odo.core.designsystem.component.OdoPermissionRationale
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoStepTransition
import com.hopcape.odo.core.designsystem.component.OdoSwitchRow
import com.hopcape.odo.core.designsystem.component.OdoSystemHandoff
import com.hopcape.odo.core.designsystem.component.OdoSystemRow
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcBellFilled
import com.hopcape.odo.core.designsystem.icons.IcBellOutlined
import com.hopcape.odo.core.designsystem.icons.IcEyeFilled
import com.hopcape.odo.core.designsystem.icons.IcLockFilled
import com.hopcape.odo.core.designsystem.icons.IcSpeedometer
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.refuel.DetectionApp
import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.feature.refuel.resources.Res
import com.hopcape.odo.feature.refuel.resources.rf_access_body
import com.hopcape.odo.feature.refuel.resources.rf_access_handoff_body
import com.hopcape.odo.feature.refuel.resources.rf_access_handoff_instruction
import com.hopcape.odo.feature.refuel.resources.rf_access_handoff_note
import com.hopcape.odo.feature.refuel.resources.rf_access_handoff_screen
import com.hopcape.odo.feature.refuel.resources.rf_access_handoff_title
import com.hopcape.odo.feature.refuel.resources.rf_access_label
import com.hopcape.odo.feature.refuel.resources.rf_access_no_others
import com.hopcape.odo.feature.refuel.resources.rf_access_no_sms
import com.hopcape.odo.feature.refuel.resources.rf_access_title
import com.hopcape.odo.feature.refuel.resources.rf_access_yes_amount
import com.hopcape.odo.feature.refuel.resources.rf_access_yes_apps
import com.hopcape.odo.feature.refuel.resources.rf_autodetect_title
import com.hopcape.odo.feature.refuel.resources.rf_autostart_action
import com.hopcape.odo.feature.refuel.resources.rf_autostart_body
import com.hopcape.odo.feature.refuel.resources.rf_autostart_done
import com.hopcape.odo.feature.refuel.resources.rf_autostart_title
import com.hopcape.odo.feature.refuel.resources.rf_background_body
import com.hopcape.odo.feature.refuel.resources.rf_background_cta
import com.hopcape.odo.feature.refuel.resources.rf_background_handoff_body
import com.hopcape.odo.feature.refuel.resources.rf_background_handoff_instruction
import com.hopcape.odo.feature.refuel.resources.rf_background_handoff_note
import com.hopcape.odo.feature.refuel.resources.rf_background_handoff_screen
import com.hopcape.odo.feature.refuel.resources.rf_background_handoff_title
import com.hopcape.odo.feature.refuel.resources.rf_background_label
import com.hopcape.odo.feature.refuel.resources.rf_background_no_idle
import com.hopcape.odo.feature.refuel.resources.rf_background_no_running
import com.hopcape.odo.feature.refuel.resources.rf_background_preview_header
import com.hopcape.odo.feature.refuel.resources.rf_background_skip
import com.hopcape.odo.feature.refuel.resources.rf_background_title
import com.hopcape.odo.feature.refuel.resources.rf_background_yes_sleep
import com.hopcape.odo.feature.refuel.resources.rf_background_yes_wake
import com.hopcape.odo.feature.refuel.resources.rf_cd_back
import com.hopcape.odo.feature.refuel.resources.rf_handoff_app_maps
import com.hopcape.odo.feature.refuel.resources.rf_handoff_app_odo
import com.hopcape.odo.feature.refuel.resources.rf_handoff_app_phonepe
import com.hopcape.odo.feature.refuel.resources.rf_handoff_app_swiggy
import com.hopcape.odo.feature.refuel.resources.rf_handoff_app_whatsapp
import com.hopcape.odo.feature.refuel.resources.rf_handoff_eyebrow
import com.hopcape.odo.feature.refuel.resources.rf_handoff_preview_label
import com.hopcape.odo.feature.refuel.resources.rf_notification_confirm
import com.hopcape.odo.feature.refuel.resources.rf_notification_edit
import com.hopcape.odo.feature.refuel.resources.rf_notification_title
import com.hopcape.odo.feature.refuel.resources.rf_optin_body
import com.hopcape.odo.feature.refuel.resources.rf_optin_continue
import com.hopcape.odo.feature.refuel.resources.rf_optin_cta
import com.hopcape.odo.feature.refuel.resources.rf_optin_cta_access
import com.hopcape.odo.feature.refuel.resources.rf_optin_cta_notify
import com.hopcape.odo.feature.refuel.resources.rf_optin_cta_notify_blocked
import com.hopcape.odo.feature.refuel.resources.rf_optin_needs_note
import com.hopcape.odo.feature.refuel.resources.rf_optin_needs_note_one
import com.hopcape.odo.feature.refuel.resources.rf_optin_no_marketing
import com.hopcape.odo.feature.refuel.resources.rf_optin_not_now
import com.hopcape.odo.feature.refuel.resources.rf_optin_preview_body
import com.hopcape.odo.feature.refuel.resources.rf_optin_preview_label
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
import com.hopcape.odo.feature.refuel.resources.rf_step_next
import com.hopcape.odo.feature.refuel.resources.rf_step_of
import org.jetbrains.compose.resources.stringResource

/**
 * The auto-detect opt-in, and the settings it becomes once it is on.
 *
 * The opt-in has to earn a permission whose own system dialog says Odo will be able to read all
 * notifications, including message text. The only fair answer to that is to say first, in Odo's
 * own words, what it will actually read and what it will not — and to say that nothing leaves the
 * phone, because that is the part the system dialog cannot tell anyone.
 *
 * One question per screen, in the order the owner would ask them: what is this for, then each
 * switch it needs, and for the two that end in a system settings page, what that page looks like
 * before they are sent to it.
 */
@Composable
internal fun AutoDetectScreen(
    state: AutoDetectUiState,
    onEvent: (AutoDetectEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.optedIn) {
        SettingsScreen(state = state, onEvent = onEvent, onBack = onBack, modifier = modifier)
        return
    }
    // The pages are one destination, so nothing else animates them. Position comes from the
    // enum's own order, which is the order the flow walks them in.
    OdoStepTransition(
        target = state.page,
        position = state.page.ordinal,
        modifier = modifier,
        label = "autoDetectPage",
    ) { page ->
        when (page) {
            AutoDetectPage.Why -> WhyPage(state, onEvent)
            AutoDetectPage.Access -> AccessPage(state, page, onEvent)
            AutoDetectPage.AccessHandoff -> AccessHandoffPage(onEvent)
            AutoDetectPage.Background -> BackgroundPage(state, page, onEvent)
            AutoDetectPage.BackgroundHandoff -> BackgroundHandoffPage(onEvent)
        }
    }
}

/**
 * Page one: what the owner gets, with no permission named.
 *
 * Nothing here is a switch, deliberately. This page has one job — letting the owner decide
 * whether they want the feature at all — and a list of Android permissions on the same screen
 * turns that decision into a form to get through. What it does say is how many switches are
 * coming, because a flow that reveals its length one screen at a time feels like it is growing.
 */
@Composable
private fun WhyPage(state: AutoDetectUiState, onEvent: (AutoDetectEvent) -> Unit) {
    val pending = state.pendingSteps
    OdoPermissionRationale(
        icon = IcBellOutlined,
        title = stringResource(Res.string.rf_optin_title),
        subtitle = stringResource(Res.string.rf_optin_body),
        benefits = emptyList(),
        assurances = emptyList(),
        // Nothing left to grant — usually a phone that granted both permissions elsewhere — so
        // this tap really does turn the feature on and has to say so.
        confirmLabel = if (pending.isEmpty()) {
            stringResource(Res.string.rf_optin_cta)
        } else {
            stringResource(Res.string.rf_optin_continue)
        },
        onConfirm = { onEvent(AutoDetectEvent.ContinueTapped) },
        dismissLabel = stringResource(Res.string.rf_optin_not_now),
        onDismiss = { onEvent(AutoDetectEvent.NotNowTapped) },
        screenTitle = stringResource(Res.string.rf_autodetect_title),
        onBack = { onEvent(AutoDetectEvent.BackTapped) },
        backContentDescription = stringResource(Res.string.rf_cd_back),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.cardGap)) {
            DraftPreview()
            OptInNote(pending.size)
        }
    }
}

/**
 * Step one: the permission detection actually runs on.
 *
 * Its primary button does not ask for anything. It moves to [AccessHandoffPage], because this
 * permission has no dialog — only a settings page the owner walks to — and being told what that
 * page looks like is worth more than being dropped on it a moment sooner.
 */
@Composable
private fun AccessPage(
    state: AutoDetectUiState,
    page: AutoDetectPage,
    onEvent: (AutoDetectEvent) -> Unit,
) {
    StepRationale(
        state = state,
        page = page,
        icon = IcEyeFilled,
        title = stringResource(Res.string.rf_access_title),
        subtitle = stringResource(Res.string.rf_access_body),
        assurancesLabel = stringResource(Res.string.rf_access_label),
        assurances = listOf(
            included(stringResource(Res.string.rf_access_yes_amount)),
            included(stringResource(Res.string.rf_access_yes_apps)),
            excluded(stringResource(Res.string.rf_access_no_others)),
            excluded(stringResource(Res.string.rf_access_no_sms)),
        ),
        confirmLabel = stringResource(Res.string.rf_step_next),
        dismissLabel = stringResource(Res.string.rf_optin_not_now),
        onDismiss = { onEvent(AutoDetectEvent.NotNowTapped) },
        onEvent = onEvent,
    )
}

/**
 * What the notification-access page will say, said first and in Odo's own words.
 *
 * Android has a single switch for notification access, so its consent screen enumerates
 * everything the permission class can cover rather than what this app does — and some OEMs dress
 * that up with a red danger sign and a risk checkbox. Nothing in the app can restyle or suppress
 * that screen; the only thing that helps is arriving at it already knowing what it says.
 *
 * The "Read all SMS" line is called out by name because it is the one that reads as a lie about
 * Odo. It is checkable: Odo declares no SMS permission at all, so anyone who doubts this can open
 * the app's permissions and see for themselves. Pointing at the receipt is worth more than asking
 * to be believed.
 */
@Composable
private fun AccessHandoffPage(onEvent: (AutoDetectEvent) -> Unit) {
    OdoSystemHandoff(
        screenTitle = stringResource(Res.string.rf_access_handoff_screen),
        onBack = { onEvent(AutoDetectEvent.BackTapped) },
        backContentDescription = stringResource(Res.string.rf_cd_back),
        eyebrow = stringResource(Res.string.rf_handoff_eyebrow),
        title = stringResource(Res.string.rf_access_handoff_title),
        body = stringResource(Res.string.rf_access_handoff_body),
        instruction = stringResource(Res.string.rf_access_handoff_instruction),
        previewLabel = stringResource(Res.string.rf_handoff_preview_label),
        previewHeader = stringResource(Res.string.rf_access_handoff_screen),
        previewRows = listOf(
            toggleRow(stringResource(Res.string.rf_handoff_app_whatsapp), on = true),
            toggleRow(stringResource(Res.string.rf_handoff_app_odo), on = false, ours = true),
            toggleRow(stringResource(Res.string.rf_handoff_app_swiggy), on = false),
        ),
        previewNote = stringResource(Res.string.rf_access_handoff_note),
        confirmLabel = stringResource(Res.string.rf_optin_cta_access),
        onConfirm = { onEvent(AutoDetectEvent.ContinueTapped) },
        dismissLabel = stringResource(Res.string.rf_optin_not_now),
        onDismiss = { onEvent(AutoDetectEvent.NotNowTapped) },
    )
}

/**
 * Step two: the one thing on this screen the app cannot ask for.
 *
 * Every phone sleeps apps to save battery, and a sleeping Odo does not see the payment
 * notification at all. Some manufacturers go further and refuse to start it in the background
 * even when the listener is enabled — which is invisible, because every switch in the app still
 * reads "on".
 *
 * Its dismiss really does turn detection on. Both Android permissions are already granted by the
 * time this page exists, so an owner who backed out here would have granted the sensitive one
 * and got nothing at all for it.
 */
@Composable
private fun BackgroundPage(
    state: AutoDetectUiState,
    page: AutoDetectPage,
    onEvent: (AutoDetectEvent) -> Unit,
) {
    StepRationale(
        state = state,
        page = page,
        icon = IcSpeedometer,
        title = stringResource(Res.string.rf_background_title),
        subtitle = stringResource(Res.string.rf_background_body),
        assurancesLabel = stringResource(Res.string.rf_background_label),
        assurances = listOf(
            included(stringResource(Res.string.rf_background_yes_wake)),
            included(stringResource(Res.string.rf_background_yes_sleep)),
            excluded(stringResource(Res.string.rf_background_no_running)),
            excluded(stringResource(Res.string.rf_background_no_idle)),
        ),
        confirmLabel = stringResource(Res.string.rf_step_next),
        dismissLabel = stringResource(Res.string.rf_background_skip),
        onDismiss = { onEvent(AutoDetectEvent.BackgroundSkipped) },
        onEvent = onEvent,
    )
}

/**
 * Where the background setting lives, which is nowhere in particular.
 *
 * Each skin names it differently and buries it somewhere else, so the page cannot promise a
 * route — only that Odo will open the closest thing this phone has, and that the row to look for
 * is Odo's own.
 */
@Composable
private fun BackgroundHandoffPage(onEvent: (AutoDetectEvent) -> Unit) {
    OdoSystemHandoff(
        screenTitle = stringResource(Res.string.rf_background_handoff_screen),
        onBack = { onEvent(AutoDetectEvent.BackTapped) },
        backContentDescription = stringResource(Res.string.rf_cd_back),
        eyebrow = stringResource(Res.string.rf_handoff_eyebrow),
        title = stringResource(Res.string.rf_background_handoff_title),
        body = stringResource(Res.string.rf_background_handoff_body),
        instruction = stringResource(Res.string.rf_background_handoff_instruction),
        previewLabel = stringResource(Res.string.rf_handoff_preview_label),
        previewHeader = stringResource(Res.string.rf_background_preview_header),
        previewRows = listOf(
            toggleRow(stringResource(Res.string.rf_handoff_app_maps), on = true),
            toggleRow(stringResource(Res.string.rf_handoff_app_odo), on = false, ours = true),
            toggleRow(stringResource(Res.string.rf_handoff_app_phonepe), on = true),
        ),
        previewNote = stringResource(Res.string.rf_background_handoff_note),
        confirmLabel = stringResource(Res.string.rf_background_cta),
        onConfirm = { onEvent(AutoDetectEvent.ContinueTapped) },
        dismissLabel = stringResource(Res.string.rf_background_skip),
        onDismiss = { onEvent(AutoDetectEvent.BackgroundSkipped) },
    )
}

/**
 * The shape every numbered ask shares: the counter, the icon, the claim, and the limits.
 *
 * One function rather than a copy per ask, so the two cannot drift apart. They are read in a row
 * by someone deciding whether to trust the app, and a step that looks different from the one
 * before it reads as a different app asking.
 */
@Composable
private fun StepRationale(
    state: AutoDetectUiState,
    page: AutoDetectPage,
    icon: ImageVector,
    title: String,
    subtitle: String,
    assurancesLabel: String,
    assurances: List<OdoPermissionAssurance>,
    confirmLabel: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onEvent: (AutoDetectEvent) -> Unit,
) {
    val number = state.stepNumberOf(page)
    val total = state.stepTotalOf(page)
    OdoPermissionRationale(
        icon = icon,
        title = title,
        subtitle = subtitle,
        benefits = emptyList(),
        assurances = assurances,
        assurancesLabel = assurancesLabel,
        confirmLabel = confirmLabel,
        onConfirm = { onEvent(AutoDetectEvent.ContinueTapped) },
        dismissLabel = dismissLabel,
        onDismiss = onDismiss,
        screenTitle = stringResource(Res.string.rf_autodetect_title),
        onBack = { onEvent(AutoDetectEvent.BackTapped) },
        backContentDescription = stringResource(Res.string.rf_cd_back),
        stepCurrent = number,
        stepTotal = total,
        stepLabel = stringResource(Res.string.rf_step_of, number, total),
    )
}

/**
 * A picture of the notification the feature produces, on the page that offers the feature.
 *
 * The whole pitch is "you will get one of these and tap once", and that is far easier to show
 * than to describe. Its numbers are an example, not the owner's data.
 */
@Composable
private fun DraftPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        OdoText(
            text = stringResource(Res.string.rf_optin_preview_label),
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textMuted,
        )
        OdoCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(PREVIEW_TILE)
                        .clip(OdoTheme.shapes.small)
                        .background(OdoTheme.colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    OdoIcon(
                        imageVector = IcBellFilled,
                        contentDescription = null,
                        tint = OdoTheme.colors.onAccent,
                        size = OdoTheme.iconSizes.small,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                    OdoText(
                        text = stringResource(Res.string.rf_notification_title),
                        style = OdoTheme.typography.label,
                    )
                    OdoText(
                        text = stringResource(Res.string.rf_optin_preview_body),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textDim,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            ) {
                FakeAction(
                    text = stringResource(Res.string.rf_notification_confirm),
                    filled = true,
                    modifier = Modifier.weight(1f),
                )
                FakeAction(
                    text = stringResource(Res.string.rf_notification_edit),
                    filled = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A button in the drawing, which cannot be pressed.
 *
 * [OdoButton] would offer a tap that does nothing and announce itself as pressable to a screen
 * reader. This is a shape with a label, on a card that is explicitly an example.
 */
@Composable
private fun FakeAction(text: String, filled: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(OdoTheme.shapes.pill)
            .background(if (filled) OdoTheme.colors.text else OdoTheme.colors.surfaceRaised)
            .padding(vertical = OdoTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        OdoText(
            text = text,
            style = OdoTheme.typography.label,
            color = if (filled) OdoTheme.colors.bg else OdoTheme.colors.text,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * What agreeing to this commits the owner to, before the first switch is asked for.
 *
 * Two things, and both belong here rather than on a page of their own. How many switches are
 * coming, because a permission flow that says nothing about its own length is the one people
 * abandon on screen two — there is no way to tell whether it ever ends. And what Odo will and
 * will not send them, which is the only part of the notifications permission that needs saying
 * once the drawn notification above has shown what it is for.
 *
 * [count] can be zero, on a phone where everything is already granted. The line about switches
 * is dropped then; the promise is not, because it is still what they are agreeing to.
 */
@Composable
private fun OptInNote(count: Int) {
    OdoCard(modifier = Modifier.fillMaxWidth(), color = OdoTheme.colors.surfaceRaised) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            OdoIcon(
                imageVector = IcLockFilled,
                contentDescription = null,
                tint = OdoTheme.colors.textMuted,
                size = OdoTheme.iconSizes.small,
                modifier = Modifier.padding(top = OdoTheme.spacing.xs),
            )
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                if (count > 0) {
                    OdoText(
                        text = if (count == 1) {
                            stringResource(Res.string.rf_optin_needs_note_one)
                        } else {
                            stringResource(Res.string.rf_optin_needs_note, count)
                        },
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textDim,
                    )
                }
                OdoText(
                    text = stringResource(Res.string.rf_optin_no_marketing),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
        }
    }
}

private fun included(text: String) =
    OdoPermissionAssurance(text = text, kind = OdoPermissionAssuranceKind.Included)

private fun excluded(text: String) =
    OdoPermissionAssurance(text = text, kind = OdoPermissionAssuranceKind.Excluded)

/** A row of the drawn system screen. The initial stands in for an app icon Odo cannot use. */
private fun toggleRow(label: String, on: Boolean, ours: Boolean = false) = OdoSystemRow(
    label = label,
    on = on,
    initial = label.take(1),
    highlighted = ours,
)

@Composable
private fun SettingsScreen(
    state: AutoDetectUiState,
    onEvent: (AutoDetectEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.rf_autodetect_title),
        onBack = onBack,
        backContentDescription = stringResource(Res.string.rf_cd_back),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            SettingsBody(state, onEvent)
        }
    }
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
                onClick = { onEvent(AutoDetectEvent.NotifyFixTapped) },
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
 * The standing reminder about the manufacturer's autostart switch.
 *
 * On these phones a separate switch decides whether Odo may be started in the background at all.
 * While it is off the system refuses to start the app, the listener never binds, and detection
 * stops the moment the owner closes Odo — with every switch on this screen still reading "on".
 * Saying so plainly is the only honest option; the button just shortens the walk.
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
        // The only way this card can ever go away. Nothing can read the setting it is about, so
        // the owner saying "done" is the signal — and advice with no way out is advice that
        // trains people to ignore the screen it is on.
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

private val PREVIEW_TILE = 32.dp

/** Page one — the outcome and the drawn notification, with no permission named. */
@OdoThemePreviews
@Composable
private fun AutoDetectWhyPreview() = OdoPreview(padded = false) {
    AutoDetectScreen(state = AutoDetectUiState(loading = false), onEvent = {}, onBack = {})
}

/** Step one of two, where the counter and the assurance card carry the argument. */
@OdoThemePreviews
@Composable
private fun AutoDetectAccessPreview() = OdoPreview(padded = false) {
    AutoDetectScreen(
        state = AutoDetectUiState(
            loading = false,
            notifyStatus = PermissionStatus.Granted,
            page = AutoDetectPage.Access,
            steps = listOf(AutoDetectStep.Access, AutoDetectStep.Background),
        ),
        onEvent = {},
        onBack = {},
    )
}

/** The forewarning about the notification-access page. */
@OdoThemePreviews
@Composable
private fun AutoDetectAccessHandoffPreview() = OdoPreview(padded = false) {
    AutoDetectScreen(
        state = AutoDetectUiState(
            loading = false,
            notifyStatus = PermissionStatus.Granted,
            page = AutoDetectPage.AccessHandoff,
            steps = listOf(AutoDetectStep.Access, AutoDetectStep.Background),
        ),
        onEvent = {},
        onBack = {},
    )
}

/** The last step, and the only one nothing can verify. */
@OdoThemePreviews
@Composable
private fun AutoDetectBackgroundPreview() = OdoPreview(padded = false) {
    AutoDetectScreen(
        state = AutoDetectUiState(
            loading = false,
            notifyStatus = PermissionStatus.Granted,
            accessGranted = true,
            page = AutoDetectPage.Background,
            steps = listOf(AutoDetectStep.Background),
        ),
        onEvent = {},
        onBack = {},
    )
}

@OdoThemePreviews
@Composable
private fun AutoDetectSettingsPreview() = OdoPreview(padded = false) {
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
