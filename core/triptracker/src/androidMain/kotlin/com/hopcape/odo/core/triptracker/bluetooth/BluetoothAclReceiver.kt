package com.hopcape.odo.core.triptracker.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.triptracker.TripTracker
import com.hopcape.odo.core.triptracker.model.VehiclePresence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Manifest-declared (see this module's `AndroidManifest.xml`) target for the platform's
 * ACL connect/disconnect broadcasts. Filters to the bonded device's MAC — the engine only
 * ever sees a [VehiclePresence] for the car it's paired with, never a stranger device — and
 * hands the result to [AclVehiclePresenceSource].
 *
 * This broadcast is often the first thing that runs in the process: the OS wakes a killed
 * app for it, and nothing UI-side has enabled the engine yet. So before delivering the
 * event, [TripTracker.armFromPersistedState] brings the engine up from the owner's stored
 * intent (bond + persisted toggle + no pause). Arming is suspend, hence [goAsync] — and a
 * failure to arm must not eat the event: the source's replay holds it for whoever
 * subscribes, so it is emitted regardless.
 */
internal class BluetoothAclReceiver : BroadcastReceiver(), KoinComponent {

    private val presenceSource: AclVehiclePresenceSource by inject()
    private val bondStore: PrefsVehicleBondStore by inject()
    private val tracker: TripTracker by inject()
    private val scope: CoroutineScope by inject()

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

        val presence = when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> VehiclePresence.Connected(address)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> VehiclePresence.Disconnected
            else -> return
        }

        // Disconnected arms too: a process woken by the car turning off must still restore
        // the persisted mid-trip session and finalize it, not sleep through its own stop.
        // goAsync() is null when onReceive is invoked directly (the test harness does) —
        // only a real broadcast dispatch carries a PendingResult to extend.
        val pending: PendingResult? = goAsync()
        scope.launch {
            runCatchingCancellableSuspend { tracker.armFromPersistedState() }
            presenceSource.onPresence(presence)
            pending?.finish()
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
