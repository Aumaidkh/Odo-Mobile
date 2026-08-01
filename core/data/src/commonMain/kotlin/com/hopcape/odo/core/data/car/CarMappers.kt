package com.hopcape.odo.core.data.car

import com.hopcape.odo.core.data.db.Cars
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * DB row → domain. The boundary where a generated [Cars] row becomes a [Car];
 * domain never sees the row type. Rehydrates via [Car.reconstitute] (trusted
 * local data, already normalized when written).
 *
 * [zone] turns the stored `created_at` instant into the day the owner added the car. It is
 * a parameter so tests can pin it, and it is read in the owner's zone rather than by
 * truncating the UTC text: a car added at 2am in Delhi was stored as the previous evening
 * in UTC, and a milestone dated the day before the owner did it reads as a bug.
 */
internal fun Cars.toDomain(zone: TimeZone = TimeZone.currentSystemDefault()): Car = Car.reconstitute(
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
    addedOn = Instant.parse(created_at).toLocalDateTime(zone).date,
)
