package com.hopcape.odo.core.data.odometer

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.odometer.CurrentOdometerProvider
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.trip.analysis.AutoOdometer
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * [CurrentOdometerProvider] over the two sources [AutoOdometer.current] combines: a car's
 * manual odometer readings and its auto-detected trips.
 *
 * A pure `combine` of two repositories already implemented in this module — nothing here
 * can fail beyond what those repositories already report, so there is no observability of
 * its own to add.
 */
internal class CurrentOdometerProviderImpl(
    private val serviceLogs: ServiceLogRepository,
    private val trips: TripRepository,
) : CurrentOdometerProvider {

    override fun observeCurrent(carId: CarId): Flow<Distance?> = combine(
        serviceLogs.observeOdometerReadings(carId),
        trips.observe(carId),
    ) { readings, allTrips -> AutoOdometer.current(readings, allTrips) }
}
