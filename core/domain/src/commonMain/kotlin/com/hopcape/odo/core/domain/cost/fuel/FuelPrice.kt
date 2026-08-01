package com.hopcape.odo.core.domain.cost.fuel

import arrow.core.Either
import arrow.core.left
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * What one unit of fuel costs on a day — ₹104.50 a litre of petrol in Pune, say. Mirrors
 * the `fuel_prices` table (DB_SCHEMA §9.13).
 *
 * [pricePerUnit] is paise, like every other money value in the app, and the unit it is
 * *per* comes from the fuel type ([unit]). [effectiveDate] is the day the price applies
 * from, not the day it was read: pump prices change daily, and a figure shown without the
 * day it belongs to cannot be judged.
 *
 * [city] is null for a rate the owner set themselves — that one follows them wherever they
 * are, and is not a claim about any city.
 */
data class FuelPrice(
    val city: String?,
    val fuelType: FuelType,
    val pricePerUnit: Amount,
    val effectiveDate: LocalDate,
    val source: FuelPriceSource,
) {
    /** Litre, kilogram or kWh — whatever [fuelType] is sold by. */
    val unit: FuelUnit get() = FuelUnit.of(fuelType)

    /** How old this price is on [today]; negative if it is dated ahead. */
    fun ageInDays(today: LocalDate): Int = effectiveDate.daysUntil(today)

    companion object {
        /** ₹1 a unit. Below this the owner meant rupees and typed paise, or slipped a key. */
        const val MIN_PAISE_PER_UNIT: Long = 100

        /** ₹1,000 a unit — far above any Indian pump, so it can only be a decimal point. */
        const val MAX_PAISE_PER_UNIT: Long = 100_000

        /**
         * Check a price the owner typed before it is stored.
         *
         * The bounds are wide on purpose: they catch a slipped decimal point, not a price
         * Odo disagrees with. What someone pays at their pump is not ours to second-guess.
         */
        fun validRate(pricePaise: Long?): Either<DomainError, Amount> =
            if (pricePaise == null || pricePaise !in MIN_PAISE_PER_UNIT..MAX_PAISE_PER_UNIT) {
                DomainError.FuelPriceOutOfRange(MIN_PAISE_PER_UNIT, MAX_PAISE_PER_UNIT).left()
            } else {
                Amount.of(pricePaise)
            }
    }
}

/**
 * Where a price came from. The screen says it out loud, because "the rate you set" and
 * "our estimate for Pune" deserve different amounts of trust.
 */
enum class FuelPriceSource {
    /** Shipped with the app — approximate, and as old as the release. */
    SEED,

    /** Refreshed from the server's weekly price feed. */
    REMOTE,

    /** The owner typed it. Beats everything else until they clear it. */
    OWNER,
}
