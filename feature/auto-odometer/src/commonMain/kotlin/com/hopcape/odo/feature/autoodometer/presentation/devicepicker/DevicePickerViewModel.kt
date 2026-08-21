package com.hopcape.odo.feature.autoodometer.presentation.devicepicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.triptracker.BluetoothRadio
import com.hopcape.odo.core.triptracker.BondedDevice
import com.hopcape.odo.core.triptracker.BondedDeviceCatalog
import com.hopcape.odo.core.triptracker.DeviceCategory
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.feature.autoodometer.domain.usecase.EnrollTriggerDevice
import com.hopcape.odo.feature.autoodometer.presentation.AutoOdometerTelemetry
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the device picker (M3) — gates the list behind `BLUETOOTH_CONNECT` *and*
 * the phone's Bluetooth radio, groups the bonded devices, and enrolls the pick as this car's
 * STEREO trigger.
 *
 * The permission itself is read at the route host, the same way the camera permission is
 * (`rememberPermissionController` is a composable, not an injectable port) — this class
 * only reacts to the status it is handed, exactly like `BillScanViewModel.permissionChanged`
 * (docs/AUTO_ODOMETER_PLAN.md §4.2). [radio] is different: it is a plain flow with no Activity
 * behind it, so it is collected here rather than being folded in from the route.
 *
 * Two gates, not one, and both have to be open before a read is attempted. Reading with the
 * radio off returns nothing useful anyway, and this screen's original bug was that it tried:
 * the catalog's A2DP callback never fires with the Bluetooth stack down, so the read never
 * finished and the spinner never stopped.
 */
internal class DevicePickerViewModel(
    private val catalog: BondedDeviceCatalog,
    private val radio: BluetoothRadio,
    private val enroll: EnrollTriggerDevice,
    private val activeCar: ActiveCarProvider,
    private val telemetry: AutoOdometerTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(DevicePickerUiState())
    val state: StateFlow<DevicePickerUiState> = _state.asStateFlow()

    private val _effects = Channel<DevicePickerEffect>(Channel.BUFFERED)
    val effects: Flow<DevicePickerEffect> = _effects.receiveAsFlow()

    /** The catalog read in flight, so a second gate opening cannot start a duplicate one. */
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            radio.enabled.collect { onEvent(DevicePickerEvent.BluetoothStateChanged(it)) }
        }
    }

    fun onEvent(event: DevicePickerEvent) {
        when (event) {
            is DevicePickerEvent.PermissionChanged -> permissionChanged(event.status)
            DevicePickerEvent.PermissionDeclined -> _state.update { it.copy(rationaleDismissed = true) }
            is DevicePickerEvent.BluetoothStateChanged -> bluetoothStateChanged(event.enabled)
            DevicePickerEvent.TurnOnBluetoothTapped -> turnOnBluetoothTapped()
            DevicePickerEvent.BluetoothSheetConfirmed -> bluetoothSheetConfirmed()
            DevicePickerEvent.BluetoothSheetDismissed -> bluetoothSheetDismissed()
            is DevicePickerEvent.DeviceSelected -> _state.update { it.copy(selectedId = event.deviceId) }
            DevicePickerEvent.UseTapped -> useTapped()
            DevicePickerEvent.NoBluetoothTapped -> noBluetoothTapped()
            DevicePickerEvent.BackTapped -> send(DevicePickerEffect.NavigateBack)
        }
    }

    private fun permissionChanged(status: PermissionStatus) {
        if (status == _state.value.permission) return
        telemetry.permissionAnswered(step = STEP_BLUETOOTH_CONNECT, status = status.name)
        _state.update { it.copy(permission = status) }
        maybeLoadDevices()
    }

    /**
     * The radio was switched on or off — from the system dialog this screen asks for, or from
     * the notification shade while the screen sat open.
     *
     * Switching off throws away whatever was on screen. A list read while the radio was on says
     * nothing true about a phone whose radio is now off, and leaving the pre-selected row up
     * would leave an enabled "Use <device>" button under a card saying Bluetooth is off.
     */
    private fun bluetoothStateChanged(enabled: Boolean) {
        if (enabled == _state.value.bluetoothEnabled) return
        if (!enabled) {
            telemetry.bluetoothOffSeen()
            loadJob?.cancel()
            loadJob = null
        }
        _state.update {
            if (enabled) {
                it.copy(bluetoothEnabled = true)
            } else {
                it.copy(bluetoothEnabled = false, devices = DeviceListLoad.Loading, selectedId = null)
            }
        }
        maybeLoadDevices()
    }

    private fun turnOnBluetoothTapped() = _state.update { it.copy(showBluetoothSheet = true) }

    /**
     * Hand off to the system's own dialog. Odo's sheet closes first: the answer comes back as a
     * [DevicePickerEvent.BluetoothStateChanged], not as a result here, so leaving the sheet up
     * would strand it under the system dialog if the owner says no.
     */
    private fun bluetoothSheetConfirmed() {
        telemetry.bluetoothEnableRequested()
        _state.update { it.copy(showBluetoothSheet = false) }
        send(DevicePickerEffect.RequestBluetoothEnable)
    }

    private fun bluetoothSheetDismissed() {
        telemetry.bluetoothEnableDeclined()
        _state.update { it.copy(showBluetoothSheet = false) }
    }

    /**
     * Reads the bonded devices, once both gates are open and nothing is already reading.
     *
     * Called from both gates rather than from whichever one happens to open last: the
     * permission grant and the radio's first reading race, and either order has to end in
     * exactly one read. [loadJob] is what makes it exactly one; an already-loaded list is left
     * alone so a re-grant on returning from settings does not reload a working screen.
     */
    private fun maybeLoadDevices() {
        val state = _state.value
        if (!state.canReadDevices) return
        if (state.devices is DeviceListLoad.Ready) return
        if (loadJob?.isActive == true) return
        loadDevices()
    }

    /**
     * Reads the bonded devices and splits them into the two sections M3 shows. A failed read
     * degrades to an empty result rather than an error state — the hint card already tells
     * the owner what to do when their device is not on screen, and that is true whether the
     * list came back empty or could not be read at all.
     */
    private fun loadDevices() {
        loadJob = viewModelScope.launch(telemetry.op(TRACE_LOAD_DEVICES)) {
            val devices = readBondedDevices()
            val (connectedNow, other) = groupByConnection(devices)
            applyLoadedDevices(connectedNow, other)
        }
    }

    /** A failed read degrades to an empty result rather than an error state (see [loadDevices]'s own doc). */
    private suspend fun readBondedDevices(): List<BondedDevice> =
        runCatchingCancellableSuspend { catalog.devices() }
            .onFailure { telemetry.nonFatal(it, stage = STAGE_CATALOG_READ) }
            .getOrDefault(emptyList())

    /** Splits into the two sections M3 shows: the connected car stereo first, everything else after. */
    private fun groupByConnection(devices: List<BondedDevice>): Pair<List<BondedDevice>, List<BondedDevice>> {
        val connectedNow = devices.filter { it.isConnectedNow && it.category == DeviceCategory.CAR_AUDIO }
        val other = devices.filterNot { it in connectedNow }
        return connectedNow to other
    }

    private fun applyLoadedDevices(connectedNow: List<BondedDevice>, other: List<BondedDevice>) {
        // The radio can go off while the read is in flight, and that reading is already stale
        // by the time it lands — dropping it keeps the radio-off card up instead of replacing
        // it with a list nothing can connect to.
        if (!_state.value.canReadDevices) return
        _state.update {
            it.copy(
                devices = DeviceListLoad.Ready(connectedNow = connectedNow, other = other),
                // Pre-select the connected car stereo (M3); leave a manual pick alone.
                selectedId = it.selectedId ?: connectedNow.firstOrNull()?.id,
            )
        }
    }

    private fun useTapped() {
        val device = _state.value.selectedDevice ?: return
        val carId = activeCar.activeCarId.value
        if (carId == null) {
            telemetry.noActiveCar()
            return
        }
        enrollDevice(carId, device)
    }

    private fun enrollDevice(carId: CarId, device: BondedDevice) {
        _state.update { it.copy(enrolling = true) }
        viewModelScope.launch(telemetry.op(TRACE_ENROLL)) {
            // EnrollTriggerDevice has no Either wrapper (F1's port signature is a plain
            // suspend fun) — a thrown exception here is unmodeled and means something is
            // broken, the same reasoning as loadDevices' catalog read. Caught rather than
            // left to crash the screen; the owner can just tap "Use" again.
            runCatchingCancellableSuspend { enroll(carId = carId, bluetoothId = device.id, mode = TriggerMode.STEREO) }
                .onSuccess { onEnrollSucceeded(device) }
                .onFailure { onEnrollFailed(it) }
        }
    }

    private fun onEnrollSucceeded(device: BondedDevice) {
        telemetry.deviceSelected(category = device.category.name, wasConnected = device.isConnectedNow)
        _state.update { it.copy(enrolling = false) }
        send(DevicePickerEffect.NavigateToPermissionSetup)
    }

    private fun onEnrollFailed(error: Throwable) {
        telemetry.nonFatal(error, stage = STAGE_ENROLL)
        _state.update { it.copy(enrolling = false) }
    }

    private fun noBluetoothTapped() {
        telemetry.noBtPathTaken()
        send(DevicePickerEffect.NavigateToNoStereoEducation)
    }

    private fun send(effect: DevicePickerEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val STEP_BLUETOOTH_CONNECT = "bluetooth_connect"
        const val TRACE_LOAD_DEVICES = "load_devices"
        const val TRACE_ENROLL = "enroll_trigger_device"
        const val STAGE_CATALOG_READ = "device_catalog_read"
        const val STAGE_ENROLL = "enroll_trigger_device"
    }
}
