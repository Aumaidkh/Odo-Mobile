package com.hopcape.odo.feature.reminders.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.odometer.CurrentOdometerProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The car's odometer today, in km — the latest manual reading plus any counted auto-trips
 * since it — what a distance-based reminder anchors at.
 *
 * `null` when the car has no reading at all, which is what disables the "by distance"
 * option rather than anchoring a reminder at a number nobody recorded.
 */
internal class ObserveCurrentOdometerUseCase(
    private val currentOdometer: CurrentOdometerProvider,
) {
    operator fun invoke(carId: CarId): Flow<Int?> =
        currentOdometer.observeCurrent(carId).map { it?.km }
}
