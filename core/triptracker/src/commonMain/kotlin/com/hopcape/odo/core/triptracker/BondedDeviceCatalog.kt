package com.hopcape.odo.core.triptracker

/**
 * Bluetooth devices bonded to this phone — the auto-odometer device picker (M3) lists
 * these to let the owner pick their car's stereo. Public, like [TripTracker]/
 * [VehicleBondStore]: `:feature:auto-odometer` is the real consumer (auto-odometer plan §2).
 */
interface BondedDeviceCatalog {
    suspend fun devices(): List<BondedDevice>
}

data class BondedDevice(
    val id: String,
    val name: String,
    val category: DeviceCategory,
    val isConnectedNow: Boolean,
)

enum class DeviceCategory { CAR_AUDIO, HEADSET, WEARABLE, OTHER }
