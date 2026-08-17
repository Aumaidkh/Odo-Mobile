package com.hopcape.odo.feature.autoodometer.presentation.devicepicker

import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.triptracker.BondedDevice

/** The device list read, as the states the owner sees: the wait, the radio being off, and the grouped result. */
internal sealed interface DeviceListLoad {

    /** [com.hopcape.odo.core.triptracker.BondedDeviceCatalog.devices] has not answered yet. */
    data object Loading : DeviceListLoad

    /**
     * The phone's Bluetooth is switched off.
     *
     * Worth its own state rather than an empty list: with the radio off there is nothing to
     * pick and nothing the hint card's advice ("start your car so the stereo connects") can
     * do about it, because the problem is on the phone rather than in the car. This is also
     * the one empty-looking case the owner can fix in a tap.
     */
    data object BluetoothOff : DeviceListLoad

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
 * Display state for the device picker (M3): the Bluetooth permission, the grouped device
 * list, and which one is picked.
 */
internal data class DevicePickerUiState(
    val permission: PermissionStatus = PermissionStatus.Askable,
    /** True once the owner chose "Not now" on the rationale — it gives way to a nudge. */
    val rationaleDismissed: Boolean = false,
    val devices: DeviceListLoad = DeviceListLoad.Loading,
    val selectedId: String? = null,
    /** True while [com.hopcape.odo.feature.autoodometer.domain.usecase.EnrollTriggerDevice] is running. */
    val enrolling: Boolean = false,
) {

    /** Show the full rationale: not granted, and the owner has not waved it away yet. */
    val showRationale: Boolean get() = permission != PermissionStatus.Granted && !rationaleDismissed

    /** Whether the nudge (once the rationale is dismissed) should offer settings. */
    val permissionBlocked: Boolean get() = permission == PermissionStatus.Blocked

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

    /** The owner chose "Not now" on the rationale. */
    data object PermissionDeclined : DevicePickerEvent

    /** "Turn on Bluetooth" on the radio-off card. */
    data object BluetoothSettingsRequested : DevicePickerEvent

    /**
     * The screen came back to the foreground. The owner may have switched the radio on while
     * they were away, and nothing else would notice: the catalog is read once and there is no
     * adapter-state subscription behind it.
     */
    data object Resumed : DevicePickerEvent

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
