package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.model.currentReading
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Distance

/**
 * The car's latest known odometer reading — what the add form starts the odometer field
 * from, so the owner adjusts a current number instead of typing one from memory.
 *
 * `null` when the car has no readings at all; the form then leaves the field empty. Read
 * once, not observed: the owner types over the prefilled value, and a later emission
 * would overwrite what they typed.
 */
internal class GetCurrentOdometerUseCase(
    private val logs: ServiceLogRepository,
) {
    suspend operator fun invoke(carId: CarId): Distance? =
        logs.odometerReadings(carId)?.currentReading()?.odometer
}
