package com.hopcape.odo.core.domain.car.model

import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CarWithOdometerTest {

    /** A stored car at 45,000 km; each test moves only the reading. */
    private val car: Car = Car.create(
        id = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        make = "Maruti",
        model = "Swift",
        year = 2020,
        fuelType = FuelType.PETROL,
        odometerKm = 45_000,
        variant = "VXI",
        registrationNumber = "mh 12 ab 1234",
        purchaseYear = 2021,
        nickname = "Bullet",
        isPrimary = true,
    ).getOrNull()!!

    @Test
    fun newReading_replacesTheOdometer() {
        val updated = car.withOdometer(48_500).getOrNull()!!

        assertEquals(48_500, updated.odometer.km)
    }

    @Test
    fun newReading_leavesEveryOtherFieldAlone() {
        val updated = car.withOdometer(48_500).getOrNull()!!

        assertEquals(car.id, updated.id)
        assertEquals(car.ownerId, updated.ownerId)
        assertEquals(car.make, updated.make)
        assertEquals(car.model, updated.model)
        assertEquals(car.variant, updated.variant)
        assertEquals(car.year.value, updated.year.value)
        assertEquals(car.fuelType, updated.fuelType)
        assertEquals(car.registrationNumber?.value, updated.registrationNumber?.value)
        assertEquals(car.purchaseYear?.value, updated.purchaseYear?.value)
        assertEquals(car.nickname, updated.nickname)
        assertEquals(car.isPrimary, updated.isPrimary)
    }

    @Test
    fun missingReading_fails() {
        val result = car.withOdometer(null)

        assertEquals(DomainError.MissingOdometer, result.leftOrNull())
    }

    @Test
    fun negativeReading_fails() {
        val result = car.withOdometer(-1)

        assertEquals(DomainError.NegativeOdometer, result.leftOrNull())
    }

    /**
     * A reading lower than the car's own is accepted here. Whether it fits the car's
     * history is the odometer timeline's call, made by the use case before this is reached.
     */
    @Test
    fun lowerReading_isNotRejectedHere() {
        val result = car.withOdometer(10_000)

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(10_000, result.getOrNull()!!.odometer.km)
    }

    @Test
    fun failedUpdate_leavesTheOriginalUntouched() {
        car.withOdometer(-1)

        assertEquals(45_000, car.odometer.km)
    }
}
