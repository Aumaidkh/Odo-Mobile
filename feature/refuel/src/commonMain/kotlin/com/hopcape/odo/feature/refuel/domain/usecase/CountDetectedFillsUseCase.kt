package com.hopcape.odo.feature.refuel.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.model.FillEntrySource
import com.hopcape.odo.core.domain.cost.repository.FuelFillRepository
import kotlinx.coroutines.flow.first

/**
 * How many fills a capture channel has actually produced.
 *
 * The auto-detect screen shows this for detection, and it is the only honest measure of
 * whether reading the owner's notifications has earned the permission it asked for. A number
 * that stays at zero is the argument for turning the whole thing off, and the screen should
 * make that easy to see rather than hide it.
 */
internal class CountDetectedFillsUseCase(
    private val fills: FuelFillRepository,
) {
    suspend operator fun invoke(carId: CarId, source: FillEntrySource): Int =
        fills.observeForCar(carId).first().count { it.entrySource == source }
}
