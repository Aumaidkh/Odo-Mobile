package com.hopcape.odo.feature.autoodometer.presentation.notifications

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoPermissionAssurance
import com.hopcape.odo.core.designsystem.component.OdoPermissionAssuranceKind
import com.hopcape.odo.core.designsystem.component.OdoPermissionRationale
import com.hopcape.odo.core.designsystem.icons.IcBellOutlined
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.feature.autoodometer.resources.Res
import com.hopcape.odo.feature.autoodometer.resources.ao_cd_back
import com.hopcape.odo.feature.autoodometer.resources.ao_flow_title
import com.hopcape.odo.feature.autoodometer.resources.ao_notify_allow
import com.hopcape.odo.feature.autoodometer.resources.ao_notify_body
import com.hopcape.odo.feature.autoodometer.resources.ao_notify_controls
import com.hopcape.odo.feature.autoodometer.resources.ao_notify_counts
import com.hopcape.odo.feature.autoodometer.resources.ao_notify_dismiss
import com.hopcape.odo.feature.autoodometer.resources.ao_notify_label
import com.hopcape.odo.feature.autoodometer.resources.ao_notify_no_marketing
import com.hopcape.odo.feature.autoodometer.resources.ao_notify_title
import org.jetbrains.compose.resources.stringResource

/**
 * Odo's own case for `POST_NOTIFICATIONS`, shown before the system prompt.
 *
 * The argument is the true one rather than the flattering one. Android will not let an app
 * measure a drive in the background unless a notification is showing, so the note is not a
 * courtesy Odo is asking permission to send — it is the thing that makes background
 * measurement legal at all. Saying that outright is also the more persuasive case, because the
 * same note carries the live distance and the pause and "not driving" controls, which is the
 * owner's only way to stop a drive being measured without opening the app.
 *
 * Same [OdoPermissionRationale] shape, title bar and back arrow as the Bluetooth rationale it
 * now precedes, so the flow reads as one run of pages rather than a detour.
 *
 * Takes no state, the way [com.hopcape.odo.feature.autoodometer.presentation.devicepicker.BluetoothRationaleScreen]
 * takes only the one flag it varies on: the copy is the same on both enrollment paths, because
 * both measure a drive the same way and the note says the same thing on each.
 */
@Composable
internal fun NotificationRationaleScreen(
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoPermissionRationale(
        modifier = modifier,
        icon = IcBellOutlined,
        title = stringResource(Res.string.ao_notify_title),
        subtitle = stringResource(Res.string.ao_notify_body),
        benefits = emptyList(),
        assurancesLabel = stringResource(Res.string.ao_notify_label),
        assurances = listOf(
            included(stringResource(Res.string.ao_notify_counts)),
            included(stringResource(Res.string.ao_notify_controls)),
            excluded(stringResource(Res.string.ao_notify_no_marketing)),
        ),
        confirmLabel = stringResource(Res.string.ao_notify_allow),
        onConfirm = onAllow,
        dismissLabel = stringResource(Res.string.ao_notify_dismiss),
        onDismiss = onSkip,
        screenTitle = stringResource(Res.string.ao_flow_title),
        onBack = onBack,
        backContentDescription = stringResource(Res.string.ao_cd_back),
    )
}

private fun included(text: String) =
    OdoPermissionAssurance(text = text, kind = OdoPermissionAssuranceKind.Included)

private fun excluded(text: String) =
    OdoPermissionAssurance(text = text, kind = OdoPermissionAssuranceKind.Excluded)

@OdoThemePreviews
@Composable
private fun NotificationRationaleScreenPreview() = OdoPreview(padded = false) {
    NotificationRationaleScreen(
        onAllow = {},
        onSkip = {},
        onBack = {},
    )
}
