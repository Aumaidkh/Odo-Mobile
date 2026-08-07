package com.hopcape.odo.core.triptracker.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hopcape.odo.core.triptracker.model.VehiclePresence
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Manifest-declared (see this module's `AndroidManifest.xml`) target for the platform's
 * ACL connect/disconnect broadcasts. Filters to the bonded device's MAC — the engine only
 * ever sees a [VehiclePresence] for the car it's paired with, never a stranger device — and
 * hands the result to [AclVehiclePresenceSource].
 */
internal class BluetoothAclReceiver : BroadcastReceiver(), KoinComponent {

    private val presenceSource: AclVehiclePresenceSource by inject()
    private val bondStore: PrefsVehicleBondStore by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val device = intent.bluetoothDeviceExtra() ?: return
        val bond = bondStore.readBond() ?: return

        // BLUETOOTH_CONNECT is a manifest permission, but the owner can still revoke the
        // runtime grant — reading .address then throws SecurityException rather than
        // returning null, so a missing grant must not crash the receiver.
        val address = try {
            device.address
        } catch (e: SecurityException) {
            return
        }
        if (address != bond.bluetoothId) return

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> presenceSource.onPresence(VehiclePresence.Connected(address))
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> presenceSource.onPresence(VehiclePresence.Disconnected)
        }
    }

    private fun Intent.bluetoothDeviceExtra(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
}
