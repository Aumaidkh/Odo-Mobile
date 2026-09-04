package com.hopcape.odo.feature.advisory.presentation

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.AmountRange
import com.hopcape.odo.feature.advisory.domain.CarValue
import com.hopcape.odo.feature.advisory.domain.CarValued
import com.hopcape.odo.feature.advisory.domain.CityTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How the estimate reads. Rounding it is the honesty rule, so it is worth pinning. */
class CarValueDisplayTest {

    @Test
    fun theRangeStatesTheCurrencyOnce() {
        val display = display(
            withFullRecord = AmountRange.ofPaise(6_40_000_00L, 6_90_000_00L),
        )

        assertEquals("Rs. 6.4L–6.9L", display.withFullRecord)
    }

    /**
     * "Rs. 34,712" would be a claim this estimate cannot support — it is built from segment
     * averages, and stating it to the rupee is the false precision the PRD forbids.
     */
    @Test
    fun theGapIsRoundedToTheNearestThousand() {
        assertEquals("+Rs. 35,000", display(recordWorth = amount(34_712_00L)).recordWorth)
        assertEquals("+Rs. 30,000", display(recordWorth = amount(29_500_00L)).recordWorth)
    }

    @Test
    fun theSummaryReadsYearModelReadingAndCity() {
        val display = display(cityName = "Srinagar")

        assertEquals("2022 Maruti Suzuki Baleno Zeta · 38,400 km · Srinagar", display.carSummary)
    }

    /** No city set is not a gap in the line; the line is simply shorter. */
    @Test
    fun anAbsentCityIsLeftOutRatherThanBlank() {
        val display = display(cityName = null)

        assertEquals("2022 Maruti Suzuki Baleno Zeta · 38,400 km", display.carSummary)
        assertTrue(!display.carSummary.endsWith(SEPARATOR))
    }

    private fun display(
        withFullRecord: AmountRange = AmountRange.ofPaise(6_40_000_00L, 6_90_000_00L),
        recordWorth: Amount = amount(35_000_00L),
        cityName: String? = "Srinagar",
    ) = CarValued(
        car = car(),
        cityName = cityName,
        cityTier = CityTier.Resolved(2),
        value = CarValue(
            today = amount(6_10_000_00L),
            withFullRecord = withFullRecord,
            recordWorth = recordWorth,
            recordCompleteness = 0.0,
            provenServices = 0,
        ),
    ).toDisplay(odometer = "38,400 km", separator = SEPARATOR)

    private fun car(): Car = Car.create(
        id = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        make = "Maruti Suzuki",
        model = "Baleno",
        variant = "Zeta",
        year = 2022,
        fuelType = FuelType.PETROL,
        odometerKm = 38_400,
        isPrimary = true,
    ).getOrElse { error("invalid fixture: $it") }

    private fun amount(paise: Long): Amount = Amount.of(paise).getOrElse { Amount.ZERO }

    private companion object {
        const val SEPARATOR = " · "
    }
}
