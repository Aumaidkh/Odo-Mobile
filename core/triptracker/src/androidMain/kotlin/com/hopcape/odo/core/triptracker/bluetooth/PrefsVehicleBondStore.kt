package com.hopcape.odo.core.triptracker.bluetooth

import android.content.Context
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.triptracker.VehicleBond
import com.hopcape.odo.core.triptracker.VehicleBondStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Private-mode `SharedPreferences` — a BT MAC + [CarId], quasi-sensitive but not worth a
 * cross-module dependency on `:core:platform`'s `SecureStore` (§4.6's own note). The value
 * never leaves the device.
 */
internal class PrefsVehicleBondStore(context: Context) : VehicleBondStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun bond(): VehicleBond? = withContext(Dispatchers.IO) { readBond() }

    override suspend fun saveBond(bond: VehicleBond) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_CAR_ID, bond.carId.value)
            .putString(KEY_MAC, bond.bluetoothId)
            .apply()
    }

    override suspend fun clearBond() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    /**
     * Synchronous read for [BluetoothAclReceiver], which can't suspend — a
     * `SharedPreferences` read is already synchronous underneath [bond]'s suspend wrapper.
     */
    fun readBond(): VehicleBond? {
        val carId = prefs.getString(KEY_CAR_ID, null) ?: return null
        val mac = prefs.getString(KEY_MAC, null) ?: return null
        return VehicleBond(CarId(carId), mac)
    }

    private companion object {
        const val PREFS_NAME = "trip_tracker_vehicle_bond"
        const val KEY_CAR_ID = "car_id"
        const val KEY_MAC = "bluetooth_mac"
    }
}
