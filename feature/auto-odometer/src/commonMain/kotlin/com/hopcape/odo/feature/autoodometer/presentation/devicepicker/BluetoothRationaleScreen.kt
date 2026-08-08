package com.hopcape.odo.feature.autoodometer.presentation.devicepicker

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoPermissionRationale
import com.hopcape.odo.core.designsystem.icons.IcCar
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.feature.autoodometer.resources.Res
import com.hopcape.odo.feature.autoodometer.resources.ao_cd_back
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_open_settings
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_permission_allow
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_permission_blocked
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_permission_dismiss
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_permission_rationale
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_permission_title
import org.jetbrains.compose.resources.stringResource

/**
 * Odo's own explanation for `BLUETOOTH_CONNECT`, shown before the system prompt (M3's
 * permission step 1). Reuses [OdoPermissionRationale] rather than a bespoke layout — the
 * same "explain first, one prompt only" shape the camera permission already uses
 * ([com.hopcape.odo.feature.billscanner.presentation.permission.CameraRationaleScreen]).
 *
 * No benefit rows: there is exactly one reason Odo wants this, and the subtitle already
 * says it — a benefit list would repeat the subtitle rather than add to it.
 *
 * @param blocked the system will no longer prompt; the primary action becomes a trip to
 *   settings and the subtitle explains that instead of asking again.
 */
@Composable
internal fun BluetoothRationaleScreen(
    blocked: Boolean,
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoPermissionRationale(
        modifier = modifier,
        icon = IcCar,
        title = stringResource(Res.string.ao_picker_permission_title),
        subtitle = if (blocked) {
            stringResource(Res.string.ao_picker_permission_blocked)
        } else {
            stringResource(Res.string.ao_picker_permission_rationale)
        },
        benefits = emptyList(),
        assurances = emptyList(),
        confirmLabel = if (blocked) {
            stringResource(Res.string.ao_permissions_open_settings)
        } else {
            stringResource(Res.string.ao_picker_permission_allow)
        },
        onConfirm = onAllow,
        dismissLabel = stringResource(Res.string.ao_picker_permission_dismiss),
        onDismiss = onNotNow,
        onClose = onBack,
        closeContentDescription = stringResource(Res.string.ao_cd_back),
    )
}

@OdoThemePreviews
@Composable
private fun BluetoothRationaleScreenPreview() = OdoPreview(padded = false) {
    BluetoothRationaleScreen(blocked = false, onAllow = {}, onNotNow = {}, onBack = {})
}

@OdoThemePreviews
@Composable
private fun BluetoothRationaleScreenBlockedPreview() = OdoPreview(padded = false) {
    BluetoothRationaleScreen(blocked = true, onAllow = {}, onNotNow = {}, onBack = {})
}
