package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.odometer.CurrentOdometerProvider
import com.hopcape.odo.core.domain.shared.Distance
import kotlinx.coroutines.flow.first

/**
 * The car's current odometer — the latest manual reading plus any counted auto-trips since
 * it — which is what the add form starts the odometer field from, so the owner adjusts a
 * current number instead of typing one from memory.
 *
 * `null` when the car has no readings at all; the form then leaves the field empty. Read
 * once, not observed: the owner types over the prefilled value, and a later emission
 * would overwrite what they typed.
 */
internal class GetCurrentOdometerUseCase(
    private val currentOdometer: CurrentOdometerProvider,
) {
    suspend operator fun invoke(carId: CarId): Distance? = currentOdometer.observeCurrent(carId).first()
}
