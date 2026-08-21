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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * `BluetoothAdapter.bondedDevices`, as the [BondedDeviceCatalog] port for the device
 * picker (M3). [isConnectedNow] cross-checks the connected-devices list of both the media
 * and hands-free profiles — bonded alone only means "paired once", not "in the car right now".
 *
 * `BLUETOOTH_CONNECT` is checked up front rather than only caught reactively: the feature
 * module's permission step is what actually asks for it, so seeing this catalog come back
 * empty *because it was never granted* is worth its own signal, separate from a genuinely
 * empty bonded list. The `SecurityException` catches below stay as a defensive fallback
 * for the gap between that check and the call (the OS can revoke mid-read) — reaching one
 * of those means something unexpected happened, so it goes through [TripTrackerTelemetry.nonFatal].
 *
 * The radio being switched off is checked up front for a harder reason: the A2DP proxy this
 * uses is a callback that simply never fires when the Bluetooth stack is not running, which
 * used to hang the whole read and leave the device picker spinning forever. Both halves of
 * that are fixed — the switched-off adapter never reaches the proxy at all, and
 * [connectedAddresses] is bounded by [PROFILE_PROXY_TIMEOUT] so no callback that fails to
 * arrive can stall a read again.
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
        // A switched-off radio has no bonded list to read and no profile service to bind to.
        // Not an error and not telemetry-worthy — the picker has its own screen for it.
        if (!adapter.isEnabledSafely()) return emptyList()

        val bonded = try {
            adapter.bondedDevices
        } catch (e: SecurityException) {
            telemetry.nonFatal(e, stage = STAGE_BONDED_DEVICES)
            return emptyList()
        }
        val connected = connectedAddressesOrEmpty(adapter)
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


    /**
     * Every address connected on a profile that means "the phone is in the car", bounded by
     * [PROFILE_PROXY_TIMEOUT].
     *
     * Both profiles are asked, because either one alone answers the wrong question. A2DP is
     * media, and a car the owner uses for calls and their own podcasts over the phone speaker
     * holds only HEADSET — reading A2DP alone called that phone "not in the car" while it
     * plainly was, which is half of issue #271. HEADSET alone would miss a stereo paired for
     * music and nothing else.
     *
     * The two are asked concurrently and share one timeout budget. Run one after the other
     * they would double the worst case, and this is called from
     * [com.hopcape.odo.core.triptracker.bluetooth.BluetoothAclReceiver]'s `goAsync()` window,
     * which does not last forever.
     *
     * "Which device is connected right now" is a nicety for the picker — it decides the
     * "CONNECTED NOW" section and which row is pre-selected — but it is load-bearing for
     * `startIfConnected`, which is what arms tracking on a phone that connected before setup
     * finished. A service that never answers costs the nicety and delays that arm to the next
     * connect broadcast; it never costs the owner the whole screen.
     */
    private suspend fun connectedAddressesOrEmpty(adapter: BluetoothAdapter): Set<String> {
        val connected = withTimeoutOrNull(PROFILE_PROXY_TIMEOUT) {
            coroutineScope {
                PRESENCE_PROFILES
                    .map { profile -> async { connectedAddresses(adapter, profile) } }
                    .awaitAll()
                    .flatten()
                    .toSet()
            }
        }
        if (connected == null) telemetry.profileProxyTimedOut()
        return connected.orEmpty()
    }

    /**
     * One profile's connected-devices list, via [BluetoothAdapter.getProfileProxy]'s callback API.
     *
     * `getProfileProxy` answers whether the *request* was accepted, not whether the service
     * will ever bind, so a `true` here is not a promise that [BluetoothProfile.ServiceListener]
     * will ever be called — with the radio off it never is. Call it through
     * [connectedAddressesOrEmpty], never directly. The listener closes the proxy before it
     * looks at the continuation, so a callback that lands after the timeout still cleans up.
     */
    private suspend fun connectedAddresses(adapter: BluetoothAdapter, profile: Int): Set<String> =
        suspendCancellableCoroutine { continuation ->
            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(connectedProfile: Int, proxy: BluetoothProfile) {
                    val addresses = try {
                        proxy.connectedDevices.mapNotNull { runCatching { it.address }.getOrNull() }.toSet()
                    } catch (e: SecurityException) {
                        telemetry.nonFatal(e, stage = STAGE_PROFILE_CONNECTED)
                        emptySet()
                    }
                    adapter.closeProfileProxy(profile, proxy)
                    if (continuation.isActive) continuation.resume(addresses)
                }

                override fun onServiceDisconnected(connectedProfile: Int) = Unit
            }
            val requested = try {
                adapter.getProfileProxy(context, listener, profile)
            } catch (e: SecurityException) {
                telemetry.nonFatal(e, stage = STAGE_PROFILE_PROXY)
                false
            }
            if (!requested && continuation.isActive) continuation.resume(emptySet())
        }

    private fun Context.hasBluetoothConnectPermission(): Boolean =
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** `isEnabled` is itself `BLUETOOTH_CONNECT`-guarded on some builds; a refusal reads as off. */
    private fun BluetoothAdapter.isEnabledSafely(): Boolean = try {
        isEnabled
    } catch (e: SecurityException) {
        telemetry.nonFatal(e, stage = STAGE_ADAPTER_ENABLED)
        false
    }

    private companion object {

        /**
         * The profiles that mean "the phone is in the car": media, and hands-free calling.
         * A car may hold either, both, or one at a time as the drive goes on.
         */
        val PRESENCE_PROFILES = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)

        /**
         * How long to wait for the profile services to bind before giving up and reporting
         * nobody connected. Shared across both queries, not per query. Generous enough for a
         * cold bind on a slow phone, short enough that the picker still draws a list rather
         * than a spinner if a service never answers.
         */
        val PROFILE_PROXY_TIMEOUT = 3.seconds

        const val STAGE_ADAPTER_ENABLED = "bonded_device_catalog_adapter_enabled"
        const val STAGE_BONDED_DEVICES = "bonded_device_catalog_bonded_devices"
        const val STAGE_DEVICE_ADDRESS = "bonded_device_catalog_device_address"
        const val STAGE_DEVICE_NAME = "bonded_device_catalog_device_name"
        const val STAGE_PROFILE_CONNECTED = "bonded_device_catalog_profile_connected"
        const val STAGE_PROFILE_PROXY = "bonded_device_catalog_profile_proxy"
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
