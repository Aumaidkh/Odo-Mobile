package com.hopcape.odo.feature.autoodometer.presentation.devicepicker

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.platform.bluetooth.SystemBluetoothSettings
import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.triptracker.BondedDevice
import com.hopcape.odo.core.triptracker.BondedDeviceCatalog
import com.hopcape.odo.core.triptracker.DeviceCategory
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.core.triptracker.VehicleBond
import com.hopcape.odo.feature.autoodometer.domain.usecase.EnrollTriggerDevice
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeVehicleBondStore
import com.hopcape.odo.feature.autoodometer.domain.usecase.TEST_CAR
import com.hopcape.odo.feature.autoodometer.presentation.AutoOdometerTelemetry
import com.hopcape.odo.feature.autoodometer.presentation.FakeActiveCarProvider
import com.hopcape.odo.feature.autoodometer.presentation.FakeBondedDeviceCatalog
import com.hopcape.odo.feature.autoodometer.presentation.RecordingAnalytics
import com.hopcape.odo.feature.autoodometer.presentation.RecordingCrash
import com.hopcape.odo.feature.autoodometer.presentation.testTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DevicePickerViewModelTest {

    private val connectedStereo = BondedDevice(
        id = "bt-1",
        name = "Swift Audio",
        category = DeviceCategory.CAR_AUDIO,
        isConnectedNow = true,
    )
    private val disconnectedStereo = BondedDevice(
        id = "bt-2",
        name = "Second Car",
        category = DeviceCategory.CAR_AUDIO,
        isConnectedNow = false,
    )
    private val earbuds = BondedDevice(
        id = "bt-3",
        name = "Boat Airdopes",
        category = DeviceCategory.HEADSET,
        isConnectedNow = true,
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        devices: List<BondedDevice> = emptyList(),
        carId: CarId? = TEST_CAR,
        bonds: FakeVehicleBondStore = FakeVehicleBondStore(),
        analytics: RecordingAnalytics = RecordingAnalytics(),
    ): Triple<DevicePickerViewModel, FakeVehicleBondStore, RecordingAnalytics> {
        val vm = DevicePickerViewModel(
            bluetoothSettings = RecordingBluetoothSettings(),
            catalog = FakeBondedDeviceCatalog(devices),
            enroll = EnrollTriggerDevice(bonds = bonds),
            activeCar = FakeActiveCarProvider(carId),
            telemetry = testTelemetry(analytics),
        )
        return Triple(vm, bonds, analytics)
    }

    @Test
    fun grantedPermission_loadsAndGroupsDevices_connectedCarAudioFirst() = runTest {
        val (vm, _, _) = viewModel(devices = listOf(earbuds, connectedStereo, disconnectedStereo))

        vm.onEvent(DevicePickerEvent.PermissionChanged(PermissionStatus.Granted))

        val loaded = assertIs<DeviceListLoad.Ready>(vm.state.value.devices)
        assertEquals(listOf(connectedStereo), loaded.connectedNow)
        assertEquals(listOf(earbuds, disconnectedStereo), loaded.other)
    }

    @Test
    fun connectedCarAudio_isPreSelectedOnLoad() = runTest {
        val (vm, _, _) = viewModel(devices = listOf(earbuds, connectedStereo))

        vm.onEvent(DevicePickerEvent.PermissionChanged(PermissionStatus.Granted))

        assertEquals(connectedStereo.id, vm.state.value.selectedId)
    }

    @Test
    fun noConnectedCarAudio_leavesNothingPreSelected() = runTest {
        val (vm, _, _) = viewModel(devices = listOf(earbuds, disconnectedStereo))

        vm.onEvent(DevicePickerEvent.PermissionChanged(PermissionStatus.Granted))

        assertNull(vm.state.value.selectedId)
    }

    @Test
    fun aManualPick_overridesThePreSelection() = runTest {
        val (vm, _, _) = viewModel(devices = listOf(connectedStereo, earbuds))
        vm.onEvent(DevicePickerEvent.PermissionChanged(PermissionStatus.Granted))

        vm.onEvent(DevicePickerEvent.DeviceSelected(earbuds.id))

        assertEquals(earbuds.id, vm.state.value.selectedId)
    }

    @Test
    fun askablePermission_showsTheRationaleUntilAnswered() = runTest {
        val (vm, _, _) = viewModel()

        assertTrue(vm.state.value.showRationale)

        vm.onEvent(DevicePickerEvent.PermissionChanged(PermissionStatus.Granted))
        assertEquals(false, vm.state.value.showRationale)
    }

    @Test
    fun blockedPermission_isReportedSeparatelyFromAskable() = runTest {
        val (vm, _, _) = viewModel()

        vm.onEvent(DevicePickerEvent.PermissionChanged(PermissionStatus.Blocked))

        assertTrue(vm.state.value.permissionBlocked)
        assertTrue(vm.state.value.showRationale, "still not granted, so the rationale (now offering settings) stays up")
    }

    @Test
    fun decliningTheRationale_dismissesItWithoutGranting() = runTest {
        val (vm, _, _) = viewModel()

        vm.onEvent(DevicePickerEvent.PermissionDeclined)

        assertEquals(false, vm.state.value.showRationale)
        assertEquals(PermissionStatus.Askable, vm.state.value.permission)
    }

    @Test
    fun useTapped_enrollsTheSelectedDeviceAsStereo_andNavigatesToPermissionSetup() = runTest {
        val (vm, bonds, analytics) = viewModel(devices = listOf(connectedStereo))
        vm.onEvent(DevicePickerEvent.PermissionChanged(PermissionStatus.Granted))

        vm.onEvent(DevicePickerEvent.UseTapped)

        assertEquals(
            VehicleBond(carId = TEST_CAR, bluetoothId = connectedStereo.id, triggerMode = TriggerMode.STEREO),
            bonds.saved.single(),
        )
        assertIs<DevicePickerEffect.NavigateToPermissionSetup>(vm.effects.first())
        assertTrue(analytics.events.any { it.first == AutoOdometerTelemetry.Event.DEVICE_SELECTED })
    }

    @Test
    fun useTapped_withNoActiveCar_doesNotEnroll() = runTest {
        val (vm, bonds, analytics) = viewModel(devices = listOf(connectedStereo), carId = null)
        vm.onEvent(DevicePickerEvent.PermissionChanged(PermissionStatus.Granted))

        vm.onEvent(DevicePickerEvent.UseTapped)

        assertTrue(bonds.saved.isEmpty())
        assertTrue(analytics.events.any { it.first == AutoOdometerTelemetry.Event.NO_ACTIVE_CAR })
    }

    @Test
    fun noBluetoothTapped_navigatesToNoStereoEducation_withoutEnrolling() = runTest {
        val (vm, bonds, analytics) = viewModel()

        vm.onEvent(DevicePickerEvent.NoBluetoothTapped)

        assertTrue(bonds.saved.isEmpty(), "NO_STEREO enrollment happens at permission setup (F6), not here")
        assertIs<DevicePickerEffect.NavigateToNoStereoEducation>(vm.effects.first())
        assertTrue(analytics.events.any { it.first == AutoOdometerTelemetry.Event.NO_BT_PATH_TAKEN })
    }

    @Test
    fun backTapped_navigatesBack() = runTest {
        val (vm, _, _) = viewModel()

        vm.onEvent(DevicePickerEvent.BackTapped)

        assertIs<DevicePickerEffect.NavigateBack>(vm.effects.first())
    }

    /**
     * `BondedDeviceCatalog.devices()` has no `Either` wrapper (F1's port signature) — a
     * thrown exception (e.g. `SecurityException` from a permission revoked mid-read) is a
     * genuine non-fatal, not a modeled `DomainError`. The list still degrades to empty
     * rather than an error screen (F5's existing behaviour); this only adds the crash
     * recording (F10).
     */
    @Test
    fun catalogReadThrows_recordsANonFatal_andDegradesToAnEmptyList() = runTest {
        val crash = RecordingCrash()
        val vm = DevicePickerViewModel(
            bluetoothSettings = RecordingBluetoothSettings(),
            catalog = FakeBondedDeviceCatalog(throwing = true),
            enroll = EnrollTriggerDevice(bonds = FakeVehicleBondStore()),
            activeCar = FakeActiveCarProvider(TEST_CAR),
            telemetry = testTelemetry(crash = crash),
        )

        vm.onEvent(DevicePickerEvent.PermissionChanged(PermissionStatus.Granted))

        val loaded = assertIs<DeviceListLoad.Ready>(vm.state.value.devices)
        assertTrue(loaded.connectedNow.isEmpty() && loaded.other.isEmpty())
        assertEquals(1, crash.recorded.size)
    }

    /**
     * `EnrollTriggerDevice` has no `Either` wrapper either — a thrown exception (a broken
     * `VehicleBondStore` write) is caught rather than crashing the screen, recorded as a
     * non-fatal, and the spinner resets so the owner can just tap "Use" again.
     */
    @Test
    fun useTapped_enrollThrows_recordsANonFatal_andResetsTheSpinner() = runTest {
        val crash = RecordingCrash()
        val bonds = FakeVehicleBondStore(throwing = true)
        val vm = DevicePickerViewModel(
            bluetoothSettings = RecordingBluetoothSettings(),
            catalog = FakeBondedDeviceCatalog(listOf(connectedStereo)),
            enroll = EnrollTriggerDevice(bonds = bonds),
            activeCar = FakeActiveCarProvider(TEST_CAR),
            telemetry = testTelemetry(crash = crash),
        )
        vm.onEvent(DevicePickerEvent.PermissionChanged(PermissionStatus.Granted))

        vm.onEvent(DevicePickerEvent.UseTapped)

        assertTrue(bonds.saved.isEmpty())
        assertEquals(1, crash.recorded.size)
        assertFalse(vm.state.value.enrolling)
    }

    @Test
    fun bluetoothOff_showsTheRadioOffStateInsteadOfAnEmptyList() = runTest {
        val vm = DevicePickerViewModel(
            bluetoothSettings = RecordingBluetoothSettings(),
            catalog = FakeBondedDeviceCatalog(bluetoothOn = false),
            enroll = EnrollTriggerDevice(bonds = FakeVehicleBondStore()),
            activeCar = FakeActiveCarProvider(TEST_CAR),
            telemetry = testTelemetry(),
        )

        vm.onEvent(DevicePickerEvent.PermissionChanged(PermissionStatus.Granted))

        assertEquals(DeviceListLoad.BluetoothOff, vm.state.value.devices)
    }

    @Test
    fun turnOnBluetooth_sendsTheOwnerToTheSettingsPage() = runTest {
        val settings = RecordingBluetoothSettings()
        val vm = DevicePickerViewModel(
            bluetoothSettings = settings,
            catalog = FakeBondedDeviceCatalog(bluetoothOn = false),
            enroll = EnrollTriggerDevice(bonds = FakeVehicleBondStore()),
            activeCar = FakeActiveCarProvider(TEST_CAR),
            telemetry = testTelemetry(),
        )
        vm.onEvent(DevicePickerEvent.PermissionChanged(PermissionStatus.Granted))

        vm.onEvent(DevicePickerEvent.BluetoothSettingsRequested)

        assertEquals(1, settings.openCount)
    }

    /** Coming back from settings with the radio on has to produce the list, not the same dead end. */
    @Test
    fun resume_afterBluetoothIsTurnedOn_loadsTheList() = runTest {
        val catalog = SwitchableBluetoothCatalog(listOf(connectedStereo))
        val vm = DevicePickerViewModel(
            bluetoothSettings = RecordingBluetoothSettings(),
            catalog = catalog,
            enroll = EnrollTriggerDevice(bonds = FakeVehicleBondStore()),
            activeCar = FakeActiveCarProvider(TEST_CAR),
            telemetry = testTelemetry(),
        )
        vm.onEvent(DevicePickerEvent.PermissionChanged(PermissionStatus.Granted))
        assertEquals(DeviceListLoad.BluetoothOff, vm.state.value.devices)

        catalog.bluetoothOn = true
        vm.onEvent(DevicePickerEvent.Resumed)

        assertIs<DeviceListLoad.Ready>(vm.state.value.devices)
    }

    /** A loaded list must survive coming back to the screen — re-reading would drop the pick. */
    @Test
    fun resume_withAListAlreadyLoaded_doesNotReadAgain() = runTest {
        val catalog = FakeBondedDeviceCatalog(listOf(connectedStereo))
        val vm = DevicePickerViewModel(
            bluetoothSettings = RecordingBluetoothSettings(),
            catalog = catalog,
            enroll = EnrollTriggerDevice(bonds = FakeVehicleBondStore()),
            activeCar = FakeActiveCarProvider(TEST_CAR),
            telemetry = testTelemetry(),
        )
        vm.onEvent(DevicePickerEvent.PermissionChanged(PermissionStatus.Granted))
        val reads = catalog.callCount

        vm.onEvent(DevicePickerEvent.Resumed)

        assertEquals(reads, catalog.callCount)
    }

}

/** Records whether the picker sent the owner to the Bluetooth settings page. */
private class RecordingBluetoothSettings(private val opens: Boolean = true) : SystemBluetoothSettings {
    var openCount = 0
        private set

    override fun open(): Boolean {
        openCount++
        return opens
    }
}

/** A catalog whose radio can be switched on between reads, the way settings does it. */
private class SwitchableBluetoothCatalog(private val devices: List<BondedDevice>) : BondedDeviceCatalog {
    var bluetoothOn = false
    override suspend fun isBluetoothOn(): Boolean = bluetoothOn
    override suspend fun devices(): List<BondedDevice> = devices
}
