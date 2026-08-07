package com.hopcape.odo.infrastructure.database.car

import com.hopcape.odo.infrastructure.database.db.Cars
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.FuelType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CarMappersTest {

    /** Pinned so the created-at conversion is tested against a zone, not the machine's. */
    private val delhi = TimeZone.of("Asia/Kolkata")

    private fun row(
        variant: String? = "ZXi",
        registration: String? = "MH12AB1234",
        purchaseYear: Long? = 2021L,
        nickname: String? = "Daily",
        isPrimary: Long = 1L,
        createdAt: String = "2026-06-30T00:00:00Z",
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
        odometer_updated_at = "2026-06-30T00:00:00Z",
        created_at = createdAt,
        updated_at = "2026-06-30T00:00:00Z",
        deleted_at = null,
        remote_version = null,
        sync_status = SyncStatus.PENDING.name,
    )

    @Test
    fun toDomain_mapsEveryField() {
        val car = row().toDomain(delhi)
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
        assertEquals(LocalDate(2026, 6, 30), car.addedOn)
    }

    @Test
    fun toDomain_readsAddedOnInTheOwnersZoneNotUtc() {
        // Stored at 20:30 UTC on the 29th, which is 2am on the 30th in Delhi. The milestone
        // has to say the day the owner added the car.
        val car = row(createdAt = "2026-06-29T20:30:00Z").toDomain(delhi)

        assertEquals(LocalDate(2026, 6, 30), car.addedOn)
    }

    @Test
    fun toDomain_handlesNullOptionalsAndNonPrimary() {
        val car = row(
            variant = null,
            registration = null,
            purchaseYear = null,
            nickname = null,
            isPrimary = 0L,
        ).toDomain(delhi)
        assertNull(car.variant)
        assertNull(car.registrationNumber)
        assertNull(car.purchaseYear)
        assertNull(car.nickname)
        assertEquals(false, car.isPrimary)
    }
}
