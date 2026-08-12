package com.hopcape.odo.feature.autoodometer.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.triptracker.TrackingPreconditions
import com.hopcape.odo.core.triptracker.TripTracker
import com.hopcape.odo.core.triptracker.VehicleBondStore
import com.hopcape.odo.feature.autoodometer.domain.model.SetupState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Builds [SetupState] for the garage card (M1) and the settings header (M7).
 *
 * [VehicleBondStore.bond] is a plain suspend getter, not a stream — there is no bond
 * change this use case would otherwise learn about. Re-reading it on every emission of
 * the reading count and the enabled flag is close enough: both change on the same
 * screens a bond does (enrollment finishes on the picker, tracking is toggled from
 * settings), so a fresh app view sees a fresh bond without a new reactive port on
 * [VehicleBondStore].
 */
internal class ObserveSetupState(
    private val serviceLogs: ServiceLogRepository,
    private val bonds: VehicleBondStore,
    private val tracker: TripTracker,
    private val preconditions: TrackingPreconditions,
) {
    operator fun invoke(carId: CarId): Flow<SetupState> = combine(
        serviceLogs.observeOdometerReadings(carId),
        tracker.isEnabled,
    ) { readings, enabled ->
        SetupState(
            readingCount = readings.size,
            bond = bonds.bond(),
            enabled = enabled,
            readiness = preconditions.status(),
        )
    }
}
