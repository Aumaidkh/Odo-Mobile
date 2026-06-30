package com.hopcape.odo.core.data.car

import com.hopcape.odo.core.data.db.Cars
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.owner.model.OwnerId

/**
 * DB row → domain. The boundary where a generated [Cars] row becomes a [Car];
 * domain never sees the row type. Rehydrates via [Car.reconstitute] (trusted
 * local data, already normalized when written).
 */
internal fun Cars.toDomain(): Car = Car.reconstitute(
    id = CarId(id),
    ownerId = OwnerId(owner_id),
    make = make,
    model = model,
    variant = variant,
    year = year.toInt(),
    fuelType = FuelType.valueOf(fuel_type),
    registrationNumber = registration_number,
    odometerKm = current_odometer_km.toInt(),
    purchaseYear = purchase_year?.toInt(),
    nickname = nickname,
    isPrimary = is_primary == 1L,
)

/** Local sync state — client-only groundwork, never sent to the domain. */
internal object SyncStatus {
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"
    const val FAILED = "FAILED"
}
