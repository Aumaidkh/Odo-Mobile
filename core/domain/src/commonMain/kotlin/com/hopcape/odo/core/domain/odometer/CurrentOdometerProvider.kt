package com.hopcape.odo.core.domain.odometer

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.shared.Distance
import kotlinx.coroutines.flow.Flow

/**
 * Port resolving a car's current odometer: the latest manual reading plus the calibrated
 * distance of every counted auto-trip since it (see
 * [AutoOdometer.current][com.hopcape.odo.core.domain.trip.analysis.AutoOdometer.current]).
 *
 * This is the aggregate, trip-aware answer to "what does the car read right now" — every
 * screen that used to read [OdometerReading][com.hopcape.odo.core.domain.servicelog.model.OdometerReading]'s
 * `currentReading()` directly reads this instead, so a car with auto-detected trips on top
 * of its last manual log shows the same number everywhere.
 *
 * `null` when the car has no manual reading at all — there is nothing to anchor from.
 */
interface CurrentOdometerProvider {

    /** The car's current odometer as a stream, so a screen updates as trips or readings change. */
    fun observeCurrent(carId: CarId): Flow<Distance?>
}
