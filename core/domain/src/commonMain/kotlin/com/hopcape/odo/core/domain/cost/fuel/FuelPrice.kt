package com.hopcape.odo.core.domain.cost.fuel

import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * What one unit of fuel costs in a city on a day — ₹104.50 a litre of petrol in Pune,
 * say. Mirrors the `fuel_prices` table (DB_SCHEMA §9.13).
 *
 * [pricePerUnit] is paise, like every other money value in the app, and the unit it is
 * *per* comes from the fuel type ([unit]). [effectiveDate] is the day the price was
 * published, not the day it was fetched: pump prices change daily, and a figure shown
 * without the day it belongs to cannot be judged.
 */
data class FuelPrice(
    val city: String,
    val fuelType: FuelType,
    val pricePerUnit: Amount,
    val effectiveDate: LocalDate,
) {
    /** Litre, kilogram or kWh — whatever [fuelType] is sold by. */
    val unit: FuelUnit get() = FuelUnit.of(fuelType)

    /** How old this price is on [today]; negative if it is dated ahead. */
    fun ageInDays(today: LocalDate): Int = effectiveDate.daysUntil(today)
}
