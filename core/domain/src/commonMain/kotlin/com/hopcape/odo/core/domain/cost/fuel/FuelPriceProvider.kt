package com.hopcape.odo.core.domain.cost.fuel

import com.hopcape.odo.core.domain.car.model.FuelType

/**
 * Port answering what fuel costs in a city.
 *
 * `null` is a normal answer, not a failure: the owner may not have set a city (onboarding
 * does not ask), or Odo may not carry prices for the one they set. Then the running cost
 * shows maintenance only, which is honest, rather than fuel priced off some other city.
 *
 * The adapter in `:core:data` reads a seeded table for now. M4's `fuel-prices` Edge
 * Function refreshes real prices weekly and lands behind this same contract, so nothing
 * above it changes.
 */
fun interface FuelPriceProvider {

    /** The latest price known for [city] and [fuelType], or `null` if there is none. */
    suspend fun priceFor(city: String, fuelType: FuelType): FuelPrice?
}
