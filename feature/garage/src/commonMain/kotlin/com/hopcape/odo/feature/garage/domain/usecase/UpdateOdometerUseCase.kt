package com.hopcape.odo.feature.garage.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.servicelog.analysis.OdometerTimeline
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Write down what the car reads today.
 *
 * The reading is checked against everything else the car has on record before it is
 * stored. An odometer only counts up, and this number is what ₹/km, the health score and
 * the resale record are all computed from — a reading that goes backwards would quietly
 * corrupt all three, so it is refused rather than warned about.
 *
 * The check is date-relative rather than "must beat the highest": history can be logged
 * backwards, so [OdometerTimeline] compares today's reading against its neighbours in time.
 * In practice that means it has to be at least the most recent earlier reading.
 */
internal class UpdateOdometerUseCase(
    private val cars: CarRepository,
    private val logs: ServiceLogRepository,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(carId: CarId, km: Int?): Either<DomainError, Car> = either {
        val car = ensureNotNull(cars.observe(carId).first()) { DomainError.CarNotFound }
        val updated = car.withOdometer(km).bind()

        // No readings at all means no such car — a live car always contributes its own.
        val known = ensureNotNull(logs.odometerReadings(carId)) { DomainError.CarNotFound }
        OdometerTimeline.validate(
            candidate = OdometerReading(
                // Not a service entry: this is the car's own reading, taken today.
                logId = null,
                date = clock.now().toLocalDateTime(timeZone).date,
                odometer = updated.odometer,
            ),
            known = known,
        ).bind()

        cars.update(updated).bind()
    }
}
