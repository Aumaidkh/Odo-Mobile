package com.hopcape.odo.core.data.cost

import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceSource
import kotlinx.datetime.LocalDate

/**
 * Pump prices per city, in paise per unit — per litre for petrol and diesel, per kilogram
 * for CNG, per kWh for electricity.
 *
 * **Best-effort reference data, not a live feed.** Fuel prices in India change daily and
 * differ by state tax, so these are approximate figures for [SEED_EFFECTIVE_DATE] and will
 * drift. They exist so the running cost has a fuel half at all before M4's `fuel-prices`
 * Edge Function ships; that function refreshes real prices weekly behind the same
 * [FuelPriceProvider][com.hopcape.odo.core.domain.cost.fuel.FuelPriceProvider] port, and
 * this file goes away when it lands.
 *
 * Keys are lowercase city names, matched against the profile's city (the profile picker
 * offers exactly these six). A city that is not here yields no price, and the running cost
 * then shows maintenance only.
 */
internal val FUEL_PRICE_SEED: Map<String, Map<FuelType, Long>> = mapOf(
    "mumbai" to mapOf(
        FuelType.PETROL to 10_444L,
        FuelType.DIESEL to 9_027L,
        FuelType.CNG to 7_900L,
        FuelType.ELECTRIC to 1_000L,
    ),
    "delhi" to mapOf(
        FuelType.PETROL to 9_477L,
        FuelType.DIESEL to 8_767L,
        FuelType.CNG to 7_559L,
        FuelType.ELECTRIC to 800L,
    ),
    "pune" to mapOf(
        FuelType.PETROL to 10_403L,
        FuelType.DIESEL to 8_989L,
        FuelType.CNG to 8_700L,
        FuelType.ELECTRIC to 1_000L,
    ),
    "bengaluru" to mapOf(
        FuelType.PETROL to 10_284L,
        FuelType.DIESEL to 8_902L,
        FuelType.CNG to 8_250L,
        FuelType.ELECTRIC to 850L,
    ),
    "chennai" to mapOf(
        FuelType.PETROL to 10_075L,
        FuelType.DIESEL to 9_244L,
        FuelType.CNG to 8_650L,
        FuelType.ELECTRIC to 700L,
    ),
    "hyderabad" to mapOf(
        FuelType.PETROL to 10_766L,
        FuelType.DIESEL to 9_582L,
        FuelType.CNG to 8_950L,
        FuelType.ELECTRIC to 900L,
    ),
)

/**
 * The day these prices are claimed to be from. Stored on every seeded row so a screen can
 * say how old the estimate is instead of implying it is today's rate, and so an app update
 * carrying corrected prices is recognised as a new batch.
 */
internal val SEED_EFFECTIVE_DATE: LocalDate = LocalDate(2026, 8, 1)

/**
 * Write the seed into the price table, once per batch.
 *
 * Skipped when this batch's date is already there, so it costs one count on every launch
 * and a later batch (a corrected seed in a new release) still gets in. Nothing is deleted:
 * older rows lose on date, and the owner's own rate outranks all of them anyway.
 */
internal fun seedFuelPrices(database: OdoDatabase) {
    val queries = database.fuelPriceQueries
    val effectiveDate = SEED_EFFECTIVE_DATE.toString()
    if (queries.countSeededOn(effectiveDate).executeAsOne() > 0L) return

    database.transaction {
        FUEL_PRICE_SEED.forEach { (city, prices) ->
            prices.forEach { (fuelType, paise) ->
                queries.insertPrice(
                    id = "seed-$city-${fuelType.name}-$effectiveDate".lowercase(),
                    city = city,
                    fuel_type = fuelType.name,
                    paise_per_unit = paise,
                    effective_date = effectiveDate,
                    source = FuelPriceSource.SEED.name,
                )
            }
        }
    }
}
