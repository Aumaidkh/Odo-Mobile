package com.hopcape.odo.feature.autoodometer.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.core.triptracker.VehicleBond
import com.hopcape.odo.core.triptracker.VehicleBondStore

/**
 * Saves the picked car-trigger identity from the device picker (M3) or the no-Bluetooth
 * branch.
 *
 * [VehicleBond.bluetoothId] only means anything for [TriggerMode.STEREO] — a
 * [TriggerMode.NO_STEREO] enrollment has no device to bond to, since it triggers off
 * activity recognition instead. Rather than making the field nullable for one caller,
 * the NO_STEREO path passes an empty string and the engine never reads it for that mode.
 *
 * Takes [carId] rather than resolving it from `ActiveCarProvider` itself — every other
 * use case in this codebase leaves that read to the ViewModel (see
 * `UpdateOdometerViewModel`'s `activeCar.activeCarId.value` null-check +
 * `telemetry.noActiveCar()`), which is also where the "no active car" UI state belongs.
 */
internal class EnrollTriggerDevice(
    private val bonds: VehicleBondStore,
) {
    suspend operator fun invoke(carId: CarId, bluetoothId: String, mode: TriggerMode) {
        bonds.saveBond(VehicleBond(carId = carId, bluetoothId = bluetoothId, triggerMode = mode))
    }
}
