package com.hopcape.odo.feature.autoodometer.presentation.devicepicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.hopcape.odo.core.designsystem.component.OdoConfirmSheet
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoListItem
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoPermissionNudge
import com.hopcape.odo.core.designsystem.component.OdoRadioButton
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCar
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.triptracker.BondedDevice
import com.hopcape.odo.core.triptracker.DeviceCategory
import com.hopcape.odo.feature.autoodometer.resources.Res
import com.hopcape.odo.feature.autoodometer.resources.ao_cd_back
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_open_settings
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_body
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_bt_sheet_body
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_bt_sheet_confirm
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_bt_sheet_dismiss
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_bt_sheet_title
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_bt_off_action
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_bt_off_body
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_bt_off_title
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_category_car_audio
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_category_headset
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_category_other
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_category_wearable
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_cta
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_hint
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_no_bluetooth
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_not_connected
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_permission_allow
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_permission_blocked
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_permission_rationale
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_section_connected
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_section_other
import com.hopcape.odo.feature.autoodometer.resources.ao_picker_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "Which one is your car?" — the trigger-device picker (M3).
 *
 * Full screen with a back arrow. Renders one of four bodies, in this order of precedence: a
 * permission nudge while Bluetooth isn't granted, the radio-off card while the phone's
 * Bluetooth is switched off, a loading spinner while the catalog read is in flight, or the
 * grouped device list once it answers. The rationale itself ([BluetoothRationaleScreen]) is a
 * full-screen takeover the route host shows instead of this screen — this composable only
 * renders what is left once that is out of the way.
 *
 * The permission and the radio get separate bodies because they are separate problems with
 * separate fixes, and telling them apart is the whole point: "let Odo use Bluetooth" is a
 * question only Odo can ask, and "switch Bluetooth on" is a switch only the owner can flip.
 * Before this, a switched-off radio produced no body at all — just the spinner, forever.
 *
 * State-free: renders [state] and forwards intents.
 */
@Composable
internal fun DevicePickerScreen(
    state: DevicePickerUiState,
    onDeviceSelected: (String) -> Unit,
    onUseTapped: () -> Unit,
    onNoBluetoothTapped: () -> Unit,
    onGrantPermission: () -> Unit,
    onTurnOnBluetooth: () -> Unit,
    onBluetoothSheetConfirm: () -> Unit,
    onBluetoothSheetDismiss: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.ao_picker_title),
        onBack = onBack,
        backContentDescription = stringResource(Res.string.ao_cd_back),
        bottomBar = {
            // Bottom edge is `sm`, not `lg`: the last button is text-only and its touch
            // target already pads the label, so `lg` read as a floating band above an opaque
            // 3-button navigation bar. Holds for both states — the "Use <device>" CTA that
            // appears on selection sits above the same text button.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = OdoTheme.spacing.screenEdge,
                        end = OdoTheme.spacing.screenEdge,
                        top = OdoTheme.spacing.lg,
                        bottom = OdoTheme.spacing.sm,
                    ),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
            ) {
                val selected = state.selectedDevice
                if (selected != null) {
                    OdoButton(
                        text = stringResource(Res.string.ao_picker_cta, selected.name),
                        onClick = onUseTapped,
                        loading = state.enrolling,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OdoButton(
                    text = stringResource(Res.string.ao_picker_no_bluetooth),
                    onClick = onNoBluetoothTapped,
                    variant = OdoButtonVariant.Tertiary,
                    enabled = !state.enrolling,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            OdoText(
                stringResource(Res.string.ao_picker_body),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
            )

            val devices = state.devices
            when {
                state.permission != PermissionStatus.Granted -> PermissionNudgeCard(state, onGrantPermission)
                state.bluetoothOff -> BluetoothOffCard(onTurnOnBluetooth)
                devices is DeviceListLoad.Loading -> LoadingRow()
                devices is DeviceListLoad.Ready -> DeviceSections(
                    load = devices,
                    selectedId = state.selectedId,
                    onDeviceSelected = onDeviceSelected,
                )
            }
        }
    }

    // A sheet, not a dialog: this is the next step of something the owner started by tapping
    // "Turn on Bluetooth", not an interruption of it, and the card explaining why stays visible
    // behind it.
    if (state.showBluetoothSheet) {
        OdoConfirmSheet(
            title = stringResource(Res.string.ao_picker_bt_sheet_title),
            body = stringResource(Res.string.ao_picker_bt_sheet_body),
            confirmLabel = stringResource(Res.string.ao_picker_bt_sheet_confirm),
            cancelLabel = stringResource(Res.string.ao_picker_bt_sheet_dismiss),
            onConfirm = onBluetoothSheetConfirm,
            onDismiss = onBluetoothSheetDismiss,
        )
    }
}

/**
 * The body shown when Odo holds the Bluetooth permission but the phone's radio is off.
 *
 * A card rather than the [OdoPermissionNudge] the permission gate uses, because there is more
 * to say and the owner has to be willing to read it: they are being asked to switch on a radio
 * for an app that is about odometers, and "why" is the whole question. The reassurance is on
 * the card, not only in the sheet, so it is still there for someone who says no and looks
 * again later.
 */
@Composable
private fun BluetoothOffCard(onTurnOn: () -> Unit) {
    OdoCard(color = OdoTheme.colors.surfaceRaised) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            OdoIcon(
                IcWarning,
                contentDescription = null,
                tint = OdoTheme.colors.warning,
                size = OdoTheme.iconSizes.small,
            )
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(stringResource(Res.string.ao_picker_bt_off_title), style = OdoTheme.typography.heading)
                OdoText(
                    stringResource(Res.string.ao_picker_bt_off_body),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
        }
        OdoButton(
            text = stringResource(Res.string.ao_picker_bt_off_action),
            onClick = onTurnOn,
            modifier = Modifier.fillMaxWidth().padding(top = OdoTheme.spacing.md),
        )
    }
}

@Composable
private fun PermissionNudgeCard(state: DevicePickerUiState, onGrantPermission: () -> Unit) {
    OdoPermissionNudge(
        icon = IcCar,
        message = if (state.permissionBlocked) {
            stringResource(Res.string.ao_picker_permission_blocked)
        } else {
            stringResource(Res.string.ao_picker_permission_rationale)
        },
        actionLabel = if (state.permissionBlocked) {
            stringResource(Res.string.ao_permissions_open_settings)
        } else {
            stringResource(Res.string.ao_picker_permission_allow)
        },
        onAction = onGrantPermission,
    )
}

@Composable
private fun LoadingRow() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        OdoLoadingIndicator()
    }
}

@Composable
private fun DeviceSections(
    load: DeviceListLoad.Ready,
    selectedId: String?,
    onDeviceSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
        if (load.connectedNow.isNotEmpty()) {
            DeviceSection(
                header = stringResource(Res.string.ao_picker_section_connected),
                devices = load.connectedNow,
                selectedId = selectedId,
                onDeviceSelected = onDeviceSelected,
            )
        }
        if (load.other.isNotEmpty()) {
            DeviceSection(
                header = stringResource(Res.string.ao_picker_section_other),
                devices = load.other,
                selectedId = selectedId,
                onDeviceSelected = onDeviceSelected,
            )
        }
        // Shown regardless of whether the list is empty or not — the owner's car may
        // simply not be in it yet (docs/AUTO_ODOMETER_PLAN.md §4.2's hint-card bullet).
        OdoCard {
            OdoText(
                stringResource(Res.string.ao_picker_hint),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
    }
}

@Composable
private fun DeviceSection(
    header: String,
    devices: List<BondedDevice>,
    selectedId: String?,
    onDeviceSelected: (String) -> Unit,
) {
    Column {
        OdoText(header, style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
        OdoCard(
            modifier = Modifier.padding(top = OdoTheme.spacing.md),
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.Top
        ) {
            devices.forEachIndexed { index, device ->
                OdoListItem(
                    headline = device.name,
                    supporting = categoryLabel(device),
                    trailing = { OdoRadioButton(selected = device.id == selectedId, onClick = null) },
                    onClick = { onDeviceSelected(device.id) },
                    modifier = Modifier.padding(horizontal = OdoTheme.spacing.md),
                )
                if (index != devices.lastIndex) OdoDivider()
            }
        }
    }
}

@Composable
private fun categoryLabel(device: BondedDevice): String {
    val base = stringResource(categoryLabelResource(device.category))
    return if (device.category == DeviceCategory.CAR_AUDIO && !device.isConnectedNow) {
        stringResource(Res.string.ao_picker_not_connected, base)
    } else {
        base
    }
}

private fun categoryLabelResource(category: DeviceCategory): StringResource = when (category) {
    DeviceCategory.CAR_AUDIO -> Res.string.ao_picker_category_car_audio
    DeviceCategory.HEADSET -> Res.string.ao_picker_category_headset
    DeviceCategory.WEARABLE -> Res.string.ao_picker_category_wearable
    DeviceCategory.OTHER -> Res.string.ao_picker_category_other
}

private fun sampleDevice(
    id: String,
    name: String,
    category: DeviceCategory,
    connected: Boolean,
) = BondedDevice(id = id, name = name, category = category, isConnectedNow = connected)

@OdoThemePreviews
@Composable
private fun DevicePickerScreenLoadingPreview() = OdoPreview(padded = false) {
    DevicePickerScreen(
        state = DevicePickerUiState(permission = PermissionStatus.Granted, devices = DeviceListLoad.Loading),
        onDeviceSelected = {},
        onUseTapped = {},
        onNoBluetoothTapped = {},
        onGrantPermission = {},
        onTurnOnBluetooth = {},
        onBluetoothSheetConfirm = {},
        onBluetoothSheetDismiss = {},
        onBack = {},
    )
}

@OdoThemePreviews
@Composable
private fun DevicePickerScreenGrantedPreview() = OdoPreview(padded = false) {
    val connected = sampleDevice("1", "Swift Audio", DeviceCategory.CAR_AUDIO, connected = true)
    DevicePickerScreen(
        state = DevicePickerUiState(
            permission = PermissionStatus.Granted,
            devices = DeviceListLoad.Ready(
                connectedNow = listOf(connected),
                other = listOf(
                    sampleDevice("2", "Boat Airdopes", DeviceCategory.HEADSET, connected = false),
                    sampleDevice("3", "Galaxy Watch", DeviceCategory.WEARABLE, connected = false),
                    sampleDevice("4", "Second Car Stereo", DeviceCategory.CAR_AUDIO, connected = false),
                ),
            ),
            selectedId = connected.id,
        ),
        onDeviceSelected = {},
        onUseTapped = {},
        onNoBluetoothTapped = {},
        onGrantPermission = {},
        onTurnOnBluetooth = {},
        onBluetoothSheetConfirm = {},
        onBluetoothSheetDismiss = {},
        onBack = {},
    )
}

@OdoThemePreviews
@Composable
private fun DevicePickerScreenEmptyPreview() = OdoPreview(padded = false) {
    DevicePickerScreen(
        state = DevicePickerUiState(
            permission = PermissionStatus.Granted,
            devices = DeviceListLoad.Ready(connectedNow = emptyList(), other = emptyList()),
        ),
        onDeviceSelected = {},
        onUseTapped = {},
        onNoBluetoothTapped = {},
        onGrantPermission = {},
        onTurnOnBluetooth = {},
        onBluetoothSheetConfirm = {},
        onBluetoothSheetDismiss = {},
        onBack = {},
    )
}

@OdoThemePreviews
@Composable
private fun DevicePickerScreenBluetoothOffPreview() = OdoPreview(padded = false) {
    DevicePickerScreen(
        state = DevicePickerUiState(permission = PermissionStatus.Granted, bluetoothEnabled = false),
        onDeviceSelected = {},
        onUseTapped = {},
        onNoBluetoothTapped = {},
        onGrantPermission = {},
        onTurnOnBluetooth = {},
        onBluetoothSheetConfirm = {},
        onBluetoothSheetDismiss = {},
        onBack = {},
    )
}

@OdoThemePreviews
@Composable
private fun DevicePickerScreenBluetoothSheetPreview() = OdoPreview(padded = false) {
    DevicePickerScreen(
        state = DevicePickerUiState(
            permission = PermissionStatus.Granted,
            bluetoothEnabled = false,
            showBluetoothSheet = true,
        ),
        onDeviceSelected = {},
        onUseTapped = {},
        onNoBluetoothTapped = {},
        onGrantPermission = {},
        onTurnOnBluetooth = {},
        onBluetoothSheetConfirm = {},
        onBluetoothSheetDismiss = {},
        onBack = {},
    )
}

@OdoThemePreviews
@Composable
private fun DevicePickerScreenBlockedPreview() = OdoPreview(padded = false) {
    DevicePickerScreen(
        state = DevicePickerUiState(permission = PermissionStatus.Blocked, rationaleDismissed = true),
        onDeviceSelected = {},
        onUseTapped = {},
        onNoBluetoothTapped = {},
        onGrantPermission = {},
        onTurnOnBluetooth = {},
        onBluetoothSheetConfirm = {},
        onBluetoothSheetDismiss = {},
        onBack = {},
    )
}
