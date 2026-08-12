package com.hopcape.odo.feature.autoodometer

import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObservePendingTripLogged
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * The auto-odometer feature's one contract with `:shared`'s app shell (plan §4.4): the
 * trip-logged moment (M6) surfaces on the next app open rather than a push notification
 * (D4), so the shell needs a way to ask "is there one to show" without importing anything
 * else from this feature. Every other declaration in this module is `internal`.
 */
interface PendingTripLoggedProvider {
    /** The newest counted trip waiting to be acknowledged, or `null` when there is none. */
    fun pending(): Flow<TripId?>
}

/**
 * Wraps [ObservePendingTripLogged] over the active car, so the shell never has to resolve
 * or pass a `carId` itself. `null` while there is no active car (onboarding not finished
 * yet) — the shell has nothing to redirect to either way.
 */
internal class DefaultPendingTripLoggedProvider(
    private val observePendingTripLogged: ObservePendingTripLogged,
    private val activeCar: ActiveCarProvider,
) : PendingTripLoggedProvider {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun pending(): Flow<TripId?> = activeCar.activeCarId.flatMapLatest { carId ->
        if (carId == null) flowOf(null) else observePendingTripLogged(carId)
    }
}
