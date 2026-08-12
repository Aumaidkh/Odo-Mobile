package com.hopcape.odo.core.domain.cost.fuel

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.LocalDate

/**
 * Port for the rate an owner sets themselves — "petrol is ₹104.40 at my pump".
 *
 * It exists so a wrong fuel estimate is fixable without waiting for a release or a
 * network: the prices Odo ships are approximate, and the ones the server will send are
 * city averages, while an owner knows what they actually paid this week.
 *
 * Deliberately **not** tied to a city. The owner is stating their own rate, not correcting
 * a city's average, so it applies wherever they are and it is the only way a car whose
 * owner never set a city gets a fuel estimate at all. It beats every other source until
 * [clearOverride] removes it.
 *
 * Read back through [FuelPriceProvider] like any other price — this port only writes, so
 * nothing has to know where the number it is reading came from.
 */
interface FuelPriceOverrides {

    /**
     * Set the owner's own price per unit for [fuelType], dated [on] (the day they entered
     * it, so the screen can show how fresh their number is). Replaces any earlier one.
     */
    suspend fun setOverride(
        fuelType: FuelType,
        pricePerUnit: Amount,
        on: LocalDate,
    ): Either<DomainError, Unit>

    /** Drop the owner's price, falling back to whatever Odo knows for their city. */
    suspend fun clearOverride(fuelType: FuelType): Either<DomainError, Unit>
}
