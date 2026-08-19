package com.hopcape.odo.feature.autoodometer.presentation.devicepicker

import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.triptracker.BondedDevice

/** The device list read, as the two states the owner sees: the wait, and the grouped result. */
internal sealed interface DeviceListLoad {

    /** [com.hopcape.odo.core.triptracker.BondedDeviceCatalog.devices] has not answered yet. */
    data object Loading : DeviceListLoad

    /**
     * The grouped result. [connectedNow] holds the bonded, currently-connected car-audio
     * devices (M3's "CONNECTED NOW" section, usually zero or one entry); [other] holds
     * every other bonded device, including a car stereo that is paired but not connected
     * right now (docs/AUTO_ODOMETER_PLAN.md §4.2). A read that failed is reported here as
     * an empty result, not a third state — the hint card ("Don't see it?") already covers
     * "nothing to pick", and there is nothing more specific to tell the owner.
     */
    data class Ready(
        val connectedNow: List<BondedDevice>,
        val other: List<BondedDevice>,
    ) : DeviceListLoad
}

/**
 * Display state for the device picker (M3): the Bluetooth permission, whether the radio is
 * switched on, the grouped device list, and which one is picked.
 *
 * The permission and the radio are two separate gates and are kept apart on purpose. Odo may
 * hold `BLUETOOTH_CONNECT` and still see nothing, because the phone's Bluetooth is off — and
 * that is not something the app can fix by asking again, so it needs its own thing said about
 * it rather than being folded into the permission nudge or, as it used to be, into a spinner
 * that never stopped.
 */
internal data class DevicePickerUiState(
    val permission: PermissionStatus = PermissionStatus.Askable,
    /** True once the owner chose "Not now" on the rationale — it gives way to a nudge. */
    val rationaleDismissed: Boolean = false,
    /**
     * Whether the phone's Bluetooth radio is on. Null until the first read comes back: the
     * screen holds the spinner rather than accusing a working radio of being off for a frame.
     */
    val bluetoothEnabled: Boolean? = null,
    /** True while the "turn on Bluetooth?" explainer sheet is up. */
    val showBluetoothSheet: Boolean = false,
    val devices: DeviceListLoad = DeviceListLoad.Loading,
    val selectedId: String? = null,
    /** True while [com.hopcape.odo.feature.autoodometer.domain.usecase.EnrollTriggerDevice] is running. */
    val enrolling: Boolean = false,
) {

    /** Show the full rationale: not granted, and the owner has not waved it away yet. */
    val showRationale: Boolean get() = permission != PermissionStatus.Granted && !rationaleDismissed

    /** Whether the nudge (once the rationale is dismissed) should offer settings. */
    val permissionBlocked: Boolean get() = permission == PermissionStatus.Blocked

    /**
     * Odo is allowed Bluetooth, but the phone's radio is off — the state the "Bluetooth is
     * off" card answers. Read strictly after the permission gate: with no permission there is
     * nothing to say about the radio yet, and saying both at once would be two asks in one
     * screen.
     */
    val bluetoothOff: Boolean get() = permission == PermissionStatus.Granted && bluetoothEnabled == false

    /** Whether the device list can be read at all right now. */
    val canReadDevices: Boolean get() = permission == PermissionStatus.Granted && bluetoothEnabled == true

    /** The picked device, resolved from [selectedId] against the loaded list. */
    val selectedDevice: BondedDevice?
        get() = (devices as? DeviceListLoad.Ready)
            ?.let { it.connectedNow + it.other }
            ?.firstOrNull { it.id == selectedId }
}

/** What the owner did on the device picker, as data. */
internal sealed interface DevicePickerEvent {

    /** The Bluetooth permission changed — answered, or re-read when the screen came back. */
    data class PermissionChanged(val status: PermissionStatus) : DevicePickerEvent

    /**
     * The phone's Bluetooth radio was switched on or off. Arrives on screen entry and on every
     * change after it, including changes made outside Odo (the notification shade).
     */
    data class BluetoothStateChanged(val enabled: Boolean) : DevicePickerEvent

    /** "Turn on Bluetooth" on the radio-off card — opens the explainer sheet. */
    data object TurnOnBluetoothTapped : DevicePickerEvent

    /** The explainer sheet's confirm button — hands off to the system's own dialog. */
    data object BluetoothSheetConfirmed : DevicePickerEvent

    /** The explainer sheet was dismissed without asking for the radio. */
    data object BluetoothSheetDismissed : DevicePickerEvent

    /** The owner chose "Not now" on the rationale. */
    data object PermissionDeclined : DevicePickerEvent

    /** A device row was tapped. */
    data class DeviceSelected(val deviceId: String) : DevicePickerEvent

    /** "Use %1$s" — enroll the selected device as the trip trigger. */
    data object UseTapped : DevicePickerEvent

    /** "My car has no Bluetooth" — the NO_STEREO escape hatch. */
    data object NoBluetoothTapped : DevicePickerEvent

    /** Back arrow / the rationale's close button. */
    data object BackTapped : DevicePickerEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface DevicePickerEffect {

    /**
     * Ask the system to switch the Bluetooth radio on
     * ([com.hopcape.odo.core.platform.bluetooth.BluetoothEnabler]). Nothing comes back: whether
     * the radio actually came on arrives as the next [DevicePickerEvent.BluetoothStateChanged],
     * which is also what covers the owner switching it on from the shade instead.
     */
    data object RequestBluetoothEnable : DevicePickerEffect

    /** Enrollment succeeded — on to permission setup (M4) for STEREO. */
    data object NavigateToPermissionSetup : DevicePickerEffect

    /**
     * The no-Bluetooth escape hatch — to the NO_STEREO education variant, not straight to
     * permission setup (docs/AUTO_ODOMETER_PLAN.md's locked flow decision). No enrollment
     * call happens here; NO_STEREO has no device to bond to, and that mode is recorded
     * where it is finally locked in — permission setup's completion step (F6).
     */
    data object NavigateToNoStereoEducation : DevicePickerEffect

    /** Pops to wherever the screen was entered from (education). */
    data object NavigateBack : DevicePickerEffect
}
