package com.hopcape.odo.core.triptracker

import com.hopcape.odo.core.domain.car.model.CarId

/** Stores the enrolled car-Bluetooth identity (single-car for now: exactly one). */
interface VehicleBondStore {
    suspend fun bond(): VehicleBond?
    suspend fun saveBond(bond: VehicleBond)
    suspend fun clearBond()
}

data class VehicleBond(val carId: CarId, val bluetoothId: String)
