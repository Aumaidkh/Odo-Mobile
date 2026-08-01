package com.hopcape.odo.core.domain.cost.fuel

import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuelEfficiencyPolicyTest {

    @Test
    fun everyFuelType_hasAnAssumedEfficiency() {
        FuelType.entries.forEach { fuelType ->
            assertTrue(FuelEfficiencyPolicy.kmPerUnit(fuelType) > 0, "$fuelType")
        }
    }

    @Test
    fun ratePerKm_dividesThePriceByTheAssumedEfficiency() {
        // ₹105/litre over 15 km/l = ₹7.00/km.
        val rate = FuelEfficiencyPolicy.ratePerKm(price(FuelType.PETROL, paise = 10_500))

        assertEquals(700L, rate.paise)
    }

    @Test
    fun ratePerKm_roundsToTheNearestPaise() {
        // ₹92.50/kg over 22 km/kg = 420.45 paise/km.
        assertEquals(420L, FuelEfficiencyPolicy.ratePerKm(price(FuelType.CNG, paise = 9_250)).paise)
        // ₹95/litre over 18 km/l = 527.78 paise/km.
        assertEquals(528L, FuelEfficiencyPolicy.ratePerKm(price(FuelType.DIESEL, paise = 9_500)).paise)
    }

    @Test
    fun electricIsPricedPerUnitDrawn() {
        // ₹8.50/kWh over 7 km/kWh = 121.43 paise/km.
        assertEquals(121L, FuelEfficiencyPolicy.ratePerKm(price(FuelType.ELECTRIC, paise = 850)).paise)
    }

    @Test
    fun fuelIsSoldByTheRightUnit() {
        assertEquals(FuelUnit.LITRE, FuelUnit.of(FuelType.PETROL))
        assertEquals(FuelUnit.LITRE, FuelUnit.of(FuelType.DIESEL))
        assertEquals(FuelUnit.KILOGRAM, FuelUnit.of(FuelType.CNG))
        assertEquals(FuelUnit.KILOWATT_HOUR, FuelUnit.of(FuelType.ELECTRIC))
        assertEquals(FuelUnit.KILOGRAM, price(FuelType.CNG, paise = 9_250).unit)
    }

    @Test
    fun aPriceKnowsHowOldItIs() {
        val fuelPrice = price(FuelType.PETROL, paise = 10_500, on = LocalDate(2026, 7, 25))

        assertEquals(7, fuelPrice.ageInDays(LocalDate(2026, 8, 1)))
    }

    private fun price(
        fuelType: FuelType,
        paise: Long,
        on: LocalDate = LocalDate(2026, 8, 1),
    ) = FuelPrice(
        city = "Pune",
        fuelType = fuelType,
        pricePerUnit = Amount.of(paise).getOrNull()!!,
        effectiveDate = on,
    )
}
