package com.hopcape.odo.feature.garage.domain.usecase

import com.hopcape.odo.core.config.FeatureConfig
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.trip.model.TripDistance
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import com.hopcape.odo.core.triptracker.TripTracker
import com.hopcape.odo.core.triptracker.VehicleBondStore
import com.hopcape.odo.feature.garage.domain.model.AutoOdometerCardState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Builds [AutoOdometerCardState] for the garage's home base (F9 of
 * docs/AUTO_ODOMETER_PLAN.md).
 *
 * A garage-local read against `:core:triptracker`'s ports, not a reuse of
 * `:feature:auto-odometer`'s own `ObserveSetupState` — features never import each other,
 * so this is Garage's own small equivalent, built against the same shared ports.
 *
 * [VehicleBondStore.bond] is a plain suspend getter, not a stream, the same choice
 * `ObserveSetupState` made: there is no bond-change signal to react to beyond the
 * screens that already re-trigger this flow (a fresh visit after enrollment finishes).
 * The monthly total is read the same one-shot way `ObserveMonthlySummary` reads it —
 * `TripRepository` has no reactive `countedBetween`.
 */
internal class ObserveAutoOdometerCardState(
    private val serviceLogs: ServiceLogRepository,
    private val bonds: VehicleBondStore,
    private val tracker: TripTracker,
    private val trips: TripRepository,
    private val clock: Clock,
    private val config: FeatureConfig,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    operator fun invoke(carId: CarId): Flow<AutoOdometerCardState> = when {
        // The garage slot is the only way into enrollment, so this is the whole feature's
        // off switch — see FeatureConfig.autoOdometerEnabled.
        !config.autoOdometerEnabled -> flowOf(AutoOdometerCardState.Hidden)
        else -> cardState(carId)
    }

    private fun cardState(carId: CarId): Flow<AutoOdometerCardState> = combine(
        serviceLogs.observeOdometerReadings(carId),
        tracker.isEnabled,
    ) { readings, enabled ->
        val setUp = bonds.bond() != null && enabled
        when {
            // Set up wins over the reading count: once enrolled, the slot always shows the
            // status tile, even for an owner who only ever typed one reading by hand.
            setUp -> AutoOdometerCardState.SetUp(monthlyKm = monthlyDistanceKm(carId))
            readings.size >= MIN_READINGS_FOR_PITCH -> AutoOdometerCardState.NotSetUp(readings.size)
            else -> AutoOdometerCardState.Hidden
        }
    }

    /** Total km across this calendar month's counted trips, floored to whole kilometres. */
    private suspend fun monthlyDistanceKm(carId: CarId): Long {
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        val startOfMonth = LocalDate(today.year, today.month, 1).atStartOfDayIn(timeZone)

        val counted = trips.countedBetween(carId, startOfMonth, now)
        return counted.fold(TripDistance.ZERO) { acc, trip -> acc + trip.distance }.toDistance().km.toLong()
    }

    private companion object {
        /** The "N times" pitch needs enough tries behind it to land — plan §1's convention. */
        const val MIN_READINGS_FOR_PITCH = 2
    }
}
