package com.hopcape.odo.core.data.car

import com.hopcape.odo.core.data.db.Cars
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.FuelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CarMappersTest {

    private fun row(
        variant: String? = "ZXi",
        registration: String? = "MH12AB1234",
        purchaseYear: Long? = 2021L,
        nickname: String? = "Daily",
        isPrimary: Long = 1L,
    ) = Cars(
        id = "car-1",
        owner_id = "owner-1",
        make = "Maruti Suzuki",
        model = "Swift",
        variant = variant,
        year = 2020L,
        fuel_type = "PETROL",
        registration_number = registration,
        current_odometer_km = 45_000L,
        purchase_year = purchaseYear,
        nickname = nickname,
        is_primary = isPrimary,
        created_at = "2026-06-30T00:00:00Z",
        updated_at = "2026-06-30T00:00:00Z",
        deleted_at = null,
        remote_version = null,
        sync_status = SyncStatus.PENDING.name,
    )

    @Test
    fun toDomain_mapsEveryField() {
        val car = row().toDomain()
        assertEquals("car-1", car.id.value)
        assertEquals("owner-1", car.ownerId.value)
        assertEquals("Maruti Suzuki", car.make)
        assertEquals("Swift", car.model)
        assertEquals("ZXi", car.variant)
        assertEquals(2020, car.year.value)
        assertEquals(FuelType.PETROL, car.fuelType)
        assertEquals("MH12AB1234", car.registrationNumber?.value)
        assertEquals(45_000, car.odometer.km)
        assertEquals(2021, car.purchaseYear?.value)
        assertEquals("Daily", car.nickname)
        assertEquals(true, car.isPrimary)
    }

    @Test
    fun toDomain_handlesNullOptionalsAndNonPrimary() {
        val car = row(
            variant = null,
            registration = null,
            purchaseYear = null,
            nickname = null,
            isPrimary = 0L,
        ).toDomain()
        assertNull(car.variant)
        assertNull(car.registrationNumber)
        assertNull(car.purchaseYear)
        assertNull(car.nickname)
        assertEquals(false, car.isPrimary)
    }
}
