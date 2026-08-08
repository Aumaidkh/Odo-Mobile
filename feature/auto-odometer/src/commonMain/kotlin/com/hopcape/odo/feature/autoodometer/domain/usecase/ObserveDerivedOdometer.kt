package com.hopcape.odo.feature.autoodometer.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.model.currentReading
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.trip.analysis.AutoOdometer
import com.hopcape.odo.core.domain.trip.model.Trip
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import com.hopcape.odo.feature.autoodometer.domain.model.DerivedOdometer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The trip-logged screen's (M6) odometer numbers for one specific trip: the drum's
 * current reading, and what it read right before that trip.
 *
 * Both readings share the same manual-reading anchor and calibration ratio
 * ([AutoOdometer.calibration]) — only the set of trips summed onto the anchor differs.
 *
 * [AutoOdometer.derived] has no date filter of its own — it sums whatever
 * [counted][com.hopcape.odo.core.domain.trip.model.TripStatus.counted] trips it is
 * handed onto the anchor (see its own test,
 * `AutoOdometerTest.derived_appliesTheCalibrationRatioToCountedTrips`, which passes only
 * the trip *after* the anchor reading). Trips that happened before the anchor's date are
 * already reflected in the anchor's own km, so summing them again would double-count
 * distance the manual reading already absorbed — this use case filters to trips whose
 * day is on or after [OdometerReading.date][com.hopcape.odo.core.domain.servicelog.model.OdometerReading.date]
 * before calling [AutoOdometer.derived]. Inclusive of the anchor's own day (rather than
 * strictly after, the way [AutoOdometer.calibration] treats a reading pair's *lower*
 * bound) so a trip driven the same day as the reading is never silently dropped.
 *
 * A rejected trip drops out of both numbers automatically the moment [RejectTrip] lands,
 * since [AutoOdometer.derived] filters to counted trips on its own.
 *
 * `null` when there is no manual reading to anchor on, or [tripId] does not match a known
 * trip (deleted data, a stale route argument).
 */
internal class ObserveDerivedOdometer(
    private val serviceLogs: ServiceLogRepository,
    private val trips: TripRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    operator fun invoke(carId: CarId, tripId: TripId): Flow<DerivedOdometer?> = combine(
        serviceLogs.observeOdometerReadings(carId),
        trips.observe(carId),
    ) { readings, allTrips ->
        val anchor = readings.currentReading() ?: return@combine null
        val target = allTrips.find { it.id == tripId } ?: return@combine null
        val calibration = AutoOdometer.calibration(readings, allTrips)
        val sinceAnchor = allTrips.filter { it.day() >= anchor.date }

        DerivedOdometer(
            current = AutoOdometer.derived(anchor, sinceAnchor, calibration),
            before = AutoOdometer.derived(
                anchor,
                sinceAnchor.filter { it.startedAt < target.startedAt },
                calibration,
            ),
        )
    }

    private fun Trip.day(): LocalDate = startedAt.toLocalDateTime(timeZone).date
}
