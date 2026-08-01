package com.hopcape.odo.feature.costtracker.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.cost.fuel.FuelPrice
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceOverrides
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Store the fuel price the owner says they pay.
 *
 * This is what keeps a wrong fuel estimate fixable: Odo's own prices are approximate and
 * as old as the last refresh, while the owner knows what the pump charged this week. Their
 * rate then wins over every other source until they clear it.
 *
 * The price is validated first, so a slipped decimal point comes back as a correctable
 * field instead of becoming a ₹1,044/litre estimate. It is stamped with today, which is
 * what lets the screen show how fresh their number is.
 */
internal class SetFuelRateUseCase(
    private val overrides: FuelPriceOverrides,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(
        fuelType: FuelType,
        pricePaise: Long?,
    ): Either<DomainError, Unit> = either {
        val rate = FuelPrice.validRate(pricePaise).bind()
        val today = clock.now().toLocalDateTime(timeZone).date
        overrides.setOverride(fuelType = fuelType, pricePerUnit = rate, on = today).bind()
    }
}
