package com.hopcape.odo.feature.autoodometer.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.trip.model.TripDistance
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import com.hopcape.odo.feature.autoodometer.domain.model.MonthlySummary
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Settings' (M7) "TRACKED THIS MONTH" card: total distance and trip count from the 1st
 * of the current calendar month to now.
 *
 * A one-shot suspend read, not a stream — unlike the rest of this feature's "Observe"
 * use cases, [TripRepository] has no reactive `countedBetween`, only the suspend one
 * this wraps (plan §4.1). Settings re-reads it on screen entry the same way it re-checks
 * [com.hopcape.odo.core.triptracker.TrackingPreconditions], rather than the module
 * growing a new reactive port for one card.
 */
internal class ObserveMonthlySummary(
    private val trips: TripRepository,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(carId: CarId): MonthlySummary {
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        val startOfMonth = LocalDate(today.year, today.month, 1).atStartOfDayIn(timeZone)

        val counted = trips.countedBetween(carId, startOfMonth, now)
        return MonthlySummary(
            totalDistance = counted.fold(TripDistance.ZERO) { acc, trip -> acc + trip.distance }.toDistance(),
            tripCount = counted.size,
        )
    }
}
