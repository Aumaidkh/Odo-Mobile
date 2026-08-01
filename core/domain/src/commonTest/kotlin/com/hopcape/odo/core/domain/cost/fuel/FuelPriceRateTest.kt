package com.hopcape.odo.core.domain.cost.fuel

import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuelPriceRateTest {

    @Test
    fun aRealPumpPriceIsAccepted() {
        assertEquals(10_440L, FuelPrice.validRate(10_440).getOrNull()?.paise)
        // The bounds themselves are valid — ₹1 and ₹1,000 a unit.
        assertTrue(FuelPrice.validRate(FuelPrice.MIN_PAISE_PER_UNIT).isRight())
        assertTrue(FuelPrice.validRate(FuelPrice.MAX_PAISE_PER_UNIT).isRight())
    }

    @Test
    fun nothingBelowARupeeOrAboveAThousandIsAPumpPrice() {
        val outOfRange = DomainError.FuelPriceOutOfRange(
            FuelPrice.MIN_PAISE_PER_UNIT,
            FuelPrice.MAX_PAISE_PER_UNIT,
        )

        assertEquals(outOfRange, FuelPrice.validRate(null).leftOrNull())
        assertEquals(outOfRange, FuelPrice.validRate(0).leftOrNull())
        assertEquals(outOfRange, FuelPrice.validRate(99).leftOrNull())
        assertEquals(outOfRange, FuelPrice.validRate(100_001).leftOrNull())
        assertEquals(outOfRange, FuelPrice.validRate(-5).leftOrNull())
    }
}
