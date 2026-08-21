package com.hopcape.odo.core.triptracker

import kotlinx.coroutines.flow.Flow

/**
 * Whether the phone's Bluetooth radio is switched on, and every change to it after that.
 *
 * Separate from [BondedDeviceCatalog] and from the `BLUETOOTH_CONNECT` permission, because
 * these are three different ways the same screen can come up empty and each one needs a
 * different thing said to the owner: the permission is Odo's to ask for, the radio is the
 * phone's to switch on, and an empty bonded list is the car's fault for never being paired.
 *
 * A [Flow] rather than a one-shot read: the owner switches the radio on from the notification
 * shade or the system dialog and comes straight back, and a screen that only read it once
 * would still be showing "Bluetooth is off" over a working radio.
 */
interface BluetoothRadio {

    /** Emits the current state on collection, and again on every change. */
    val enabled: Flow<Boolean>
}
