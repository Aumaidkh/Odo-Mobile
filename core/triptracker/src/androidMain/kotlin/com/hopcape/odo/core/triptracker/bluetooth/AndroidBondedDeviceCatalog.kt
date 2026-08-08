package com.hopcape.odo.core.triptracker.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import com.hopcape.odo.core.triptracker.BondedDevice
import com.hopcape.odo.core.triptracker.BondedDeviceCatalog
import com.hopcape.odo.core.triptracker.DeviceCategory
import com.hopcape.odo.core.triptracker.observability.TripTrackerTelemetry
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * `BluetoothAdapter.bondedDevices`, as the [BondedDeviceCatalog] port for the device
 * picker (M3). [isConnectedNow] cross-checks the A2DP profile's connected-devices list —
 * bonded alone only means "paired once", not "in the car right now".
 *
 * `BLUETOOTH_CONNECT` is checked up front rather than only caught reactively: the feature
 * module's permission step is what actually asks for it, so seeing this catalog come back
 * empty *because it was never granted* is worth its own signal, separate from a genuinely
 * empty bonded list. The `SecurityException` catches below stay as a defensive fallback
 * for the gap between that check and the call (the OS can revoke mid-read) — reaching one
 * of those means something unexpected happened, so it goes through [TripTrackerTelemetry.nonFatal].
 */
internal class AndroidBondedDeviceCatalog(
    private val context: Context,
    private val telemetry: TripTrackerTelemetry,
) : BondedDeviceCatalog {
    override suspend fun devices(): List<BondedDevice> {
        if (!context.hasBluetoothConnectPermission()) {
            telemetry.catalogPermissionMissing()
            return emptyList()
        }

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        val bonded = try {
            adapter.bondedDevices
        } catch (e: SecurityException) {
            telemetry.nonFatal(e, stage = STAGE_BONDED_DEVICES)
            return emptyList()
        }
        val connected = connectedA2dpAddresses(adapter)
        return bonded.map { device ->
            val btClass = device.bluetoothClass
            val category = if (btClass != null) classify(btClass.majorDeviceClass, btClass.deviceClass) else DeviceCategory.OTHER
            val address = try {
                device.address
            } catch (e: SecurityException) {
                telemetry.nonFatal(e, stage = STAGE_DEVICE_ADDRESS)
                return@map null
            }
            val name = try {
                device.name ?: address
            } catch (e: SecurityException) {
                telemetry.nonFatal(e, stage = STAGE_DEVICE_NAME)
                address
            }
            BondedDevice(id = address, name = name, category = category, isConnectedNow = address in connected)
        }.filterNotNull()
    }

    /** A2DP's connected-devices list, via [BluetoothAdapter.getProfileProxy]'s callback API. */
    private suspend fun connectedA2dpAddresses(adapter: BluetoothAdapter): Set<String> =
        suspendCancellableCoroutine { continuation ->
            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    val addresses = try {
                        proxy.connectedDevices.mapNotNull { runCatching { it.address }.getOrNull() }.toSet()
                    } catch (e: SecurityException) {
                        telemetry.nonFatal(e, stage = STAGE_A2DP_CONNECTED)
                        emptySet()
                    }
                    adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                    if (continuation.isActive) continuation.resume(addresses)
                }

                override fun onServiceDisconnected(profile: Int) = Unit
            }
            val requested = try {
                adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
            } catch (e: SecurityException) {
                telemetry.nonFatal(e, stage = STAGE_A2DP_PROXY)
                false
            }
            if (!requested && continuation.isActive) continuation.resume(emptySet())
        }

    private fun Context.hasBluetoothConnectPermission(): Boolean =
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val STAGE_BONDED_DEVICES = "bonded_device_catalog_bonded_devices"
        const val STAGE_DEVICE_ADDRESS = "bonded_device_catalog_device_address"
        const val STAGE_DEVICE_NAME = "bonded_device_catalog_device_name"
        const val STAGE_A2DP_CONNECTED = "bonded_device_catalog_a2dp_connected"
        const val STAGE_A2DP_PROXY = "bonded_device_catalog_a2dp_proxy"
    }
}

/**
 * Pure classification off [android.bluetooth.BluetoothClass]'s two getters — testable with
 * no real adapter or device. [majorDeviceClass] and [deviceClass] are exactly
 * `BluetoothClass.majorDeviceClass`/`.deviceClass`'s raw ints at the call site.
 */
internal fun classify(majorDeviceClass: Int, deviceClass: Int): DeviceCategory = when {
    deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> DeviceCategory.CAR_AUDIO
    deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
        deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> DeviceCategory.HEADSET
    majorDeviceClass == BluetoothClass.Device.Major.WEARABLE -> DeviceCategory.WEARABLE
    else -> DeviceCategory.OTHER
}
