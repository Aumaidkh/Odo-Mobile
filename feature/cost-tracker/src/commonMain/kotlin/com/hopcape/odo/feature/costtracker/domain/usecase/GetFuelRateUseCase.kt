package com.hopcape.odo.feature.costtracker.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.cost.fuel.FuelPrice
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceProvider
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The fuel price the running cost is currently built on, for the sheet that lets the owner
 * correct it.
 *
 * A read of its own rather than a slice of the running-cost snapshot: the sheet opens on a
 * price and a fuel type, and nothing else, and it must still work for a car whose figures
 * cannot be computed at all (no odometer readings yet, but the owner already knows what
 * petrol costs them).
 *
 * `null` means there is no car, so there is nothing to price.
 */
internal class GetFuelRateUseCase(
    private val cars: CarRepository,
    private val city: CurrentCityProvider,
    private val fuelPrices: FuelPriceProvider,
) {
    suspend operator fun invoke(carId: CarId): FuelRateSnapshot? {
        val car = cars.observe(carId).first() ?: return null
        return FuelRateSnapshot(
            fuelType = car.fuelType,
            price = fuelPrices.priceFor(city.currentCity(), car.fuelType),
        )
    }

    /**
     * The same read, again whenever the stored prices change.
     *
     * The sheet is a navigation destination, so its ViewModel outlives one visit: reading
     * once when it was built would leave a reopened sheet showing the price from before the
     * owner's last correction.
     */
    fun observe(carId: CarId): Flow<FuelRateSnapshot?> =
        fuelPrices.priceChanges().map { invoke(carId) }
}

/**
 * What the fuel-rate sheet needs: what the car runs on, and the price in force — Odo's for
 * the owner's city, the owner's own, or none at all.
 */
internal data class FuelRateSnapshot(
    val fuelType: FuelType,
    val price: FuelPrice?,
)
