package com.hopcape.odo.feature.reminders.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The car's odometer reading today, in km — what a distance-based reminder anchors at.
 *
 * `null` when the car has no reading at all, which is what disables the "by distance"
 * option rather than anchoring a reminder at a number nobody recorded.
 */
internal class ObserveCurrentOdometerUseCase(
    private val serviceLogs: ServiceLogRepository,
) {
    operator fun invoke(carId: CarId): Flow<Int?> =
        serviceLogs.observeOdometerReadings(carId).map { readings ->
            readings.maxWithOrNull(compareBy({ it.date }, { it.odometer.km }))?.odometer?.km
        }
}
