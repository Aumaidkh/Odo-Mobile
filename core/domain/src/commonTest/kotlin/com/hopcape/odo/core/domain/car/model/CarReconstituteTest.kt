package com.hopcape.odo.core.domain.car.model

import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlin.test.Test
import kotlin.test.assertEquals

class CarReconstituteTest {

    private val id = CarId("car-1")
    private val ownerId = OwnerId("owner-1")

    @Test
    fun createThenReconstitute_preservesEveryField() {
        val created = Car.create(
            id = id,
            ownerId = ownerId,
            make = "Maruti",
            model = "Swift",
            year = 2020,
            fuelType = FuelType.PETROL,
            odometerKm = 45_000,
            variant = "ZXi",
            registrationNumber = "mh 12 ab 1234",
            purchaseYear = 2021,
            nickname = "Daily",
            isPrimary = true,
        ).getOrNull()!!

        // Rehydrate from the normalized/primitive values a DB row would carry.
        val rehydrated = Car.reconstitute(
            id = created.id,
            ownerId = created.ownerId,
            make = created.make,
            model = created.model,
            variant = created.variant,
            year = created.year.value,
            fuelType = created.fuelType,
            registrationNumber = created.registrationNumber?.value,
            odometerKm = created.odometer.km,
            purchaseYear = created.purchaseYear?.value,
            nickname = created.nickname,
            isPrimary = created.isPrimary,
        )

        assertEquals(created.id, rehydrated.id)
        assertEquals(created.ownerId, rehydrated.ownerId)
        assertEquals(created.make, rehydrated.make)
        assertEquals(created.model, rehydrated.model)
        assertEquals(created.variant, rehydrated.variant)
        assertEquals(created.year, rehydrated.year)
        assertEquals(created.fuelType, rehydrated.fuelType)
        assertEquals(created.registrationNumber, rehydrated.registrationNumber)
        assertEquals(created.odometer, rehydrated.odometer)
        assertEquals(created.purchaseYear, rehydrated.purchaseYear)
        assertEquals(created.nickname, rehydrated.nickname)
        assertEquals(created.isPrimary, rehydrated.isPrimary)
    }

    @Test
    fun reconstitute_withNullOptionals_isStable() {
        val car = Car.reconstitute(
            id = id,
            ownerId = ownerId,
            make = "Tata",
            model = "Nexon",
            variant = null,
            year = 2019,
            fuelType = FuelType.DIESEL,
            registrationNumber = null,
            odometerKm = 0,
            purchaseYear = null,
            nickname = null,
            isPrimary = false,
        )

        assertEquals(null, car.variant)
        assertEquals(null, car.registrationNumber)
        assertEquals(null, car.purchaseYear)
        assertEquals(null, car.nickname)
        assertEquals(0, car.odometer.km)
    }
}
