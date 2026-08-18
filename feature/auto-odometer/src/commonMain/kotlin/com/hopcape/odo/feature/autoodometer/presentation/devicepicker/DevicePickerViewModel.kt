package com.hopcape.odo.feature.autoodometer.presentation.devicepicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.triptracker.BondedDevice
import com.hopcape.odo.core.platform.bluetooth.SystemBluetoothSettings
import com.hopcape.odo.core.triptracker.BondedDeviceCatalog
import com.hopcape.odo.core.triptracker.DeviceCategory
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.feature.autoodometer.domain.usecase.EnrollTriggerDevice
import com.hopcape.odo.feature.autoodometer.presentation.AutoOdometerTelemetry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the device picker (M3) — gates the list behind `BLUETOOTH_CONNECT`,
 * groups the bonded devices, and enrolls the pick as this car's STEREO trigger.
 *
 * The permission itself is read at the route host, the same way the camera permission is
 * (`rememberPermissionController` is a composable, not an injectable port) — this class
 * only reacts to the status it is handed, exactly like `BillScanViewModel.permissionChanged`
 * (docs/AUTO_ODOMETER_PLAN.md §4.2).
 */
internal class DevicePickerViewModel(
    private val catalog: BondedDeviceCatalog,
    private val bluetoothSettings: SystemBluetoothSettings,
    private val enroll: EnrollTriggerDevice,
    private val activeCar: ActiveCarProvider,
    private val telemetry: AutoOdometerTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(DevicePickerUiState())
    val state: StateFlow<DevicePickerUiState> = _state.asStateFlow()

    private val _effects = Channel<DevicePickerEffect>(Channel.BUFFERED)
    val effects: Flow<DevicePickerEffect> = _effects.receiveAsFlow()

    fun onEvent(event: DevicePickerEvent) {
        when (event) {
            is DevicePickerEvent.PermissionChanged -> permissionChanged(event.status)
            DevicePickerEvent.BluetoothSettingsRequested -> bluetoothSettingsRequested()
            DevicePickerEvent.Resumed -> resumed()
            DevicePickerEvent.PermissionDeclined -> _state.update { it.copy(rationaleDismissed = true) }
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
        // Only the first grant kicks off the read — a later re-grant (owner leaves and comes
        // back from settings) should not restart a list that already loaded.
        if (status == PermissionStatus.Granted && _state.value.devices is DeviceListLoad.Loading) {
            loadDevices()
        }
    }

    /**
     * Reads the bonded devices and splits them into the two sections M3 shows. A failed read
     * degrades to an empty result rather than an error state — the hint card already tells
     * the owner what to do when their device is not on screen, and that is true whether the
     * list came back empty or could not be read at all.
     */
    private fun loadDevices() {
        viewModelScope.launch(telemetry.op(TRACE_LOAD_DEVICES)) {
            if (!isBluetoothOn()) {
                _state.update { it.copy(devices = DeviceListLoad.BluetoothOff) }
                return@launch
            }
            val devices = readBondedDevices()
            val (connectedNow, other) = groupByConnection(devices)
            applyLoadedDevices(connectedNow, other)
        }
    }

    /** A failed read is treated as "on", so a catalog problem shows the list's own empty case rather than blaming the radio. */
    private suspend fun isBluetoothOn(): Boolean =
        runCatching { catalog.isBluetoothOn() }
            .onFailure { telemetry.nonFatal(it, stage = STAGE_CATALOG_READ) }
            .getOrDefault(true)

    private fun bluetoothSettingsRequested() {
        bluetoothSettings.open()
    }

    /**
     * Re-read on the way back from the Bluetooth settings page. Only from the radio-off state:
     * a list that already loaded must not be thrown away and rebuilt every time the screen
     * comes forward, which would drop the owner's selection.
     */
    private fun resumed() {
        if (_state.value.devices !is DeviceListLoad.BluetoothOff) return
        loadDevices()
    }

    /** A failed read degrades to an empty result rather than an error state (see [loadDevices]'s own doc). */
    private suspend fun readBondedDevices(): List<BondedDevice> =
        runCatching { catalog.devices() }
            .onFailure { telemetry.nonFatal(it, stage = STAGE_CATALOG_READ) }
            .getOrDefault(emptyList())

    /** Splits into the two sections M3 shows: the connected car stereo first, everything else after. */
    private fun groupByConnection(devices: List<BondedDevice>): Pair<List<BondedDevice>, List<BondedDevice>> {
        val connectedNow = devices.filter { it.isConnectedNow && it.category == DeviceCategory.CAR_AUDIO }
        val other = devices.filterNot { it in connectedNow }
        return connectedNow to other
    }

    private fun applyLoadedDevices(connectedNow: List<BondedDevice>, other: List<BondedDevice>) {
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
            runCatching { enroll(carId = carId, bluetoothId = device.id, mode = TriggerMode.STEREO) }
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
