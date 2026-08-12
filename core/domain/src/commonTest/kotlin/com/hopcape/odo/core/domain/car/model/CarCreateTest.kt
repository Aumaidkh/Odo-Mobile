package com.hopcape.odo.core.domain.car.model

import arrow.core.EitherNel
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CarCreateTest {

    private val id = CarId("car-1")
    private val ownerId = OwnerId("owner-1")

    /** Valid baseline; each test overrides only the fields it exercises. */
    private fun create(
        make: String? = "Maruti",
        model: String? = "Swift",
        year: Int? = 2020,
        fuelType: FuelType? = FuelType.PETROL,
        odometerKm: Int? = 45_000,
        variant: String? = null,
        registrationNumber: String? = "mh 12 ab 1234",
        purchaseYear: Int? = 2021,
        nickname: String? = null,
    ): EitherNel<DomainError, Car> = Car.create(
        id = id,
        ownerId = ownerId,
        make = make,
        model = model,
        year = year,
        fuelType = fuelType,
        odometerKm = odometerKm,
        variant = variant,
        registrationNumber = registrationNumber,
        purchaseYear = purchaseYear,
        nickname = nickname,
    )

    @Test
    fun validInput_buildsCar() {
        val result = create()
        assertTrue(result.isRight(), "expected Right but was $result")
        val car = result.getOrNull()!!
        assertEquals("Maruti", car.make)
        assertEquals(2020, car.year.value)
        assertEquals(FuelType.PETROL, car.fuelType)
        assertEquals(45_000, car.odometer.km)
        assertEquals("MH12AB1234", car.registrationNumber?.value)
        assertEquals(2021, car.purchaseYear?.value)
    }

    @Test
    fun trimsAndNullsBlankOptionalFields() {
        val car = create(make = "  Tata  ", variant = "   ", nickname = "").getOrNull()!!
        assertEquals("Tata", car.make)
        assertEquals(null, car.variant)
        assertEquals(null, car.nickname)
    }

    @Test
    fun multipleInvalidFields_accumulateAllErrors() {
        val errors = create(
            make = "  ",        // blank
            model = null,       // blank
            year = 1900,        // out of range
            fuelType = null,    // missing
            odometerKm = -5,    // negative
        ).leftOrNull()
        assertTrue(errors != null, "expected Left with errors")
        assertTrue(DomainError.BlankMake in errors)
        assertTrue(DomainError.BlankModel in errors)
        assertTrue(DomainError.YearOutOfRange("year", 1900) in errors)
        assertTrue(DomainError.MissingFuelType in errors)
        assertTrue(DomainError.NegativeOdometer in errors)
        assertEquals(5, errors.size)
    }

    @Test
    fun missingOdometer_isRejected() {
        val errors = create(odometerKm = null).leftOrNull()
        assertTrue(errors != null && DomainError.MissingOdometer in errors)
    }
}
