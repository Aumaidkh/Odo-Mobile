package com.hopcape.odo.core.triptracker

import com.hopcape.odo.core.domain.car.model.CarId

/** Stores the enrolled car-Bluetooth identity (single-car for now: exactly one). */
interface VehicleBondStore {
    suspend fun bond(): VehicleBond?
    suspend fun saveBond(bond: VehicleBond)
    suspend fun clearBond()
}

/**
 * How the enrolled car's trips are triggered, decided per-enrollment
 * (auto-odometer plan §1.1). [STEREO] arms only the bonded stereo's presence trigger,
 * confirmed by a GPS speed gate. [NO_STEREO] arms only the activity-recognition motion
 * trigger. The engine never arms both for the same car.
 */
enum class TriggerMode { STEREO, NO_STEREO }

data class VehicleBond(val carId: CarId, val bluetoothId: String, val triggerMode: TriggerMode)
