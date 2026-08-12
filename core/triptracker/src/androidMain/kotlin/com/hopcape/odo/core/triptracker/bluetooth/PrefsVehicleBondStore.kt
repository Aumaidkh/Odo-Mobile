package com.hopcape.odo.core.triptracker.bluetooth

import android.content.Context
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.triptracker.TriggerMode
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
            .putString(KEY_TRIGGER_MODE, bond.triggerMode.name)
            .apply()
    }

    override suspend fun clearBond() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    /**
     * Synchronous read for [BluetoothAclReceiver], which can't suspend — a
     * `SharedPreferences` read is already synchronous underneath [bond]'s suspend wrapper.
     * A row missing or with an unreadable trigger mode reads as no bond at all — every
     * bond written by this class always carries one, so a partial row means corruption,
     * not an older schema to migrate.
     */
    fun readBond(): VehicleBond? {
        val carId = prefs.getString(KEY_CAR_ID, null) ?: return null
        val mac = prefs.getString(KEY_MAC, null) ?: return null
        val triggerMode = prefs.getString(KEY_TRIGGER_MODE, null)?.let {
            runCatching { TriggerMode.valueOf(it) }.getOrNull()
        } ?: return null
        return VehicleBond(CarId(carId), mac, triggerMode)
    }

    private companion object {
        const val PREFS_NAME = "trip_tracker_vehicle_bond"
        const val KEY_CAR_ID = "car_id"
        const val KEY_MAC = "bluetooth_mac"
        const val KEY_TRIGGER_MODE = "trigger_mode"
    }
}
