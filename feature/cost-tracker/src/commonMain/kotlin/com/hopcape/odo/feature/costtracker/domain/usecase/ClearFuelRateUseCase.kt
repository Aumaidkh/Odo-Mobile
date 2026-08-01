package com.hopcape.odo.feature.costtracker.domain.usecase

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceOverrides
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Drop the owner's own fuel price, so the estimate falls back to what Odo knows for their
 * city. Clearing a rate that was never set succeeds — the end state is what was asked for.
 */
internal class ClearFuelRateUseCase(
    private val overrides: FuelPriceOverrides,
) {
    suspend operator fun invoke(fuelType: FuelType): Either<DomainError, Unit> =
        overrides.clearOverride(fuelType)
}
