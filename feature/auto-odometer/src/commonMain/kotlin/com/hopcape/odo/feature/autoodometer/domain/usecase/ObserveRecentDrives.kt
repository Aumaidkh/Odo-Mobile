package com.hopcape.odo.feature.autoodometer.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.shared.formatDayMonth
import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import com.hopcape.odo.feature.autoodometer.domain.model.RecentDrive
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The last few drives, for the background-location step to argue from (M4).
 *
 * Returns every recent trip rather than only the counted ones, because the missed drives are
 * the whole argument: a `GAP_INFERRED` trip is the record of the car having moved while nothing
 * was watching, and filtering it out would leave the step making its case from the drives that
 * already worked.
 *
 * Empty during a first-time setup, which is the normal case — education, picker and permissions
 * all happen before the car has moved once. The screen drops the card rather than inventing one.
 *
 * A one-shot read of the repository's stream, not a subscription: nothing about the past changes
 * while a permission screen is open.
 */
internal class ObserveRecentDrives(
    private val trips: TripRepository,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(carId: CarId, limit: Int = DEFAULT_LIMIT): List<RecentDrive> {
        val today = clock.now().toLocalDateTime(timeZone).date
        return trips.observe(carId)
            .first()
            .sortedByDescending { it.startedAt }
            .take(limit)
            .map { trip ->
                val date = trip.startedAt.toLocalDateTime(timeZone).date
                RecentDrive(
                    dayLabel = formatDayMonth(date),
                    isToday = date == today,
                    distanceKm = trip.distance.toDistance().km,
                    caught = trip.mode != TripMode.GAP_INFERRED,
                )
            }
    }

    private companion object {
        /** Enough to show a pattern, few enough to read at a glance. */
        const val DEFAULT_LIMIT = 3
    }
}
