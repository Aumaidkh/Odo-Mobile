package com.hopcape.odo.core.domain.cost.fuel

import com.hopcape.odo.core.domain.car.model.FuelType

/**
 * Port answering what fuel costs.
 *
 * `null` is a normal answer, not a failure: the owner may not have set a city (onboarding
 * does not ask), Odo may not carry prices for the one they set, and they may not have
 * typed a rate of their own. Then the running cost shows maintenance only, which is
 * honest, rather than fuel priced off some other city.
 *
 * The adapter in `:core:data` reads the local price table, which ships seeded and is
 * refreshed by the server's weekly feed once M4's `fuel-prices` function lands. A rate the
 * owner set themselves wins over both.
 */
fun interface FuelPriceProvider {

    /**
     * The price to use for [fuelType], or `null` if nothing is known.
     *
     * [city] may be null or blank — the owner has not set one — and then only a rate they
     * typed themselves can answer, since Odo has no idea which city's pump to quote.
     */
    suspend fun priceFor(city: String?, fuelType: FuelType): FuelPrice?
}
