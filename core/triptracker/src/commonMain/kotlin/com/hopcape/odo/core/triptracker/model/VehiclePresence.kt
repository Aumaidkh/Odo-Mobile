package com.hopcape.odo.core.triptracker.model

/** A Bluetooth ACL connect/disconnect event for the bonded vehicle device. */
internal sealed interface VehiclePresence {
    data class Connected(val bluetoothId: String) : VehiclePresence
    data object Disconnected : VehiclePresence
}
