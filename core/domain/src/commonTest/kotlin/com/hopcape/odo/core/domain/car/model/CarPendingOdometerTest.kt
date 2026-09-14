package com.hopcape.odo.core.domain.car.model

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A car whose owner has not read the odometer yet.
 *
 * The reading itself stays non-null so the column, the mappers and every consumer keep
 * working; the flag is what stops a placeholder zero being read as a measurement.
 */
class CarPendingOdometerTest {

    @Test
    fun aSkippedReading_storesZero_andReportsNoKnownOdometer() {
        val car = car(odometerKm = null, odometerPending = true)

        assertTrue(car.isOdometerPending)
        assertEquals(0, car.odometer.km, "the row still holds a number")
        assertNull(car.knownOdometer, "but nothing may compute from it")
    }

    @Test
    fun aGivenReading_isNotPending() {
        val car = car(odometerKm = 45_000, odometerPending = false)

        assertFalse(car.isOdometerPending)
        assertEquals(45_000, car.knownOdometer?.km)
    }

    @Test
    fun writingAReading_answersThePendingQuestion() {
        val car = car(odometerKm = null, odometerPending = true)
            .withOdometer(45_000)
            .getOrElse { error("expected a valid reading") }

        assertFalse(car.isOdometerPending)
        assertEquals(45_000, car.knownOdometer?.km)
    }

    private fun car(odometerKm: Int?, odometerPending: Boolean): Car = Car.create(
        id = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        make = "Maruti Suzuki",
        model = "Swift",
        year = 2021,
        fuelType = FuelType.PETROL,
        odometerKm = odometerKm,
        odometerPending = odometerPending,
        registrationNumber = "MH12AB1234",
    ).getOrElse { error("expected a valid car, got $it") }
}
