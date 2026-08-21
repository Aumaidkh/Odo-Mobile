package com.hopcape.odo.core.triptracker.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.hopcape.odo.core.triptracker.BluetoothRadio
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * `BluetoothAdapter.isEnabled`, plus the system's own `ACTION_STATE_CHANGED` broadcast so a
 * change is noticed while a screen is open.
 *
 * The broadcast carries the new state in an extra, which is ignored: the adapter is re-read on
 * every one instead. `isEnabled` is true only in `STATE_ON`, so re-reading collapses the four
 * states (on, off, turning on, turning off) into the one answer a screen can act on, with no
 * table to keep in step with the platform's.
 *
 * No permission is needed to register for this — it is a protected system broadcast, and
 * `BLUETOOTH_CONNECT` guards reading *devices*, not the radio's own switch. That is what lets
 * the device picker tell "Odo was never allowed Bluetooth" apart from "the radio is off".
 */
internal class AndroidBluetoothRadio(private val context: Context) : BluetoothRadio {

    override val enabled: Flow<Boolean> = callbackFlow {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            // No Bluetooth hardware at all. Emit once and stay open rather than closing the
            // flow: a collector that sees the stream end would have no state to render.
            send(false)
            awaitClose { }
            return@callbackFlow
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(adapter.isEnabled)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Seeded after registering, not before: a radio switched on in the gap between the two
        // would otherwise be missed by both the seed and the receiver.
        send(adapter.isEnabled)

        awaitClose { context.unregisterReceiver(receiver) }
    }.conflate().distinctUntilChanged()
}
