package com.hopcape.odo.core.domain.cost.fuel

import kotlin.test.Test
import kotlin.test.assertEquals

class FuelEfficiencyUnitTest {

    @Test
    fun distancePerUnit_readsAsMileageDoesInIndia() {
        assertEquals("15 km/l", FuelEfficiencyUnit.DISTANCE_PER_UNIT.format(15, FuelUnit.LITRE))
        assertEquals("22 km/l", FuelEfficiencyUnit.DISTANCE_PER_UNIT.format(22, FuelUnit.LITRE))
        assertEquals("7 km/l", FuelEfficiencyUnit.DISTANCE_PER_UNIT.format(7, FuelUnit.LITRE))
    }

    @Test
    fun unitsPer100Km_invertsTheFigureToOneDecimalPlace() {
        assertEquals("6.7 L/100km", FuelEfficiencyUnit.UNITS_PER_100KM.format(15, FuelUnit.LITRE))
        assertEquals("5.6 L/100km", FuelEfficiencyUnit.UNITS_PER_100KM.format(18, FuelUnit.LITRE))
        assertEquals("4.5 kg/100km", FuelEfficiencyUnit.UNITS_PER_100KM.format(22, FuelUnit.KILOGRAM))
        assertEquals("14.3 kWh/100km", FuelEfficiencyUnit.UNITS_PER_100KM.format(7, FuelUnit.KILOWATT_HOUR))
    }

    @Test
    fun bothFormsDescribeTheSameCar() {
        val kmPerLitre = FuelEfficiencyPolicy.kmPerUnit(com.hopcape.odo.core.domain.car.model.FuelType.PETROL)
        assertEquals("15 km/l", FuelEfficiencyUnit.DISTANCE_PER_UNIT.format(kmPerLitre, FuelUnit.LITRE))
        assertEquals("6.7 L/100km", FuelEfficiencyUnit.UNITS_PER_100KM.format(kmPerLitre, FuelUnit.LITRE))
    }
}
