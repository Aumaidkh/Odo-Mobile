package com.hopcape.odo.core.triptracker

/**
 * Bluetooth devices bonded to this phone — the auto-odometer device picker (M3) lists
 * these to let the owner pick their car's stereo. Public, like [TripTracker]/
 * [VehicleBondStore]: `:feature:auto-odometer` is the real consumer (auto-odometer plan §2).
 */
interface BondedDeviceCatalog {
    suspend fun devices(): List<BondedDevice>

    /**
     * Whether the phone's Bluetooth radio is on.
     *
     * [devices] cannot tell the difference on its own: a switched-off adapter reports no
     * bonded devices, which reads exactly like a phone that has never paired with anything.
     * The picker needs to tell those apart — one is a dead end, the other is one tap from
     * being fixed.
     *
     * Defaults to true so an implementation on a platform without a radio to switch, or a
     * test fake that does not care, is unaffected.
     */
    suspend fun isBluetoothOn(): Boolean = true
}

data class BondedDevice(
    val id: String,
    val name: String,
    val category: DeviceCategory,
    val isConnectedNow: Boolean,
)

enum class DeviceCategory { CAR_AUDIO, HEADSET, WEARABLE, OTHER }
