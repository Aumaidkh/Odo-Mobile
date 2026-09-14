package com.hopcape.odo.feature.advisory.domain

import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.city.CityCatalog
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The owner's car and what it is worth, kept current as the record grows.
 *
 * A stream rather than a read, because the figure is the point of the screen and it moves:
 * scanning the bill the screen just asked for has to change the number behind it while the
 * owner is still looking at it.
 *
 * The city is resolved to a tier here rather than in the estimator, so the estimator stays
 * pure and the catalog lookup — which can fail, and often has nothing to answer with — has
 * one place to degrade.
 *
 * It reports what it could not resolve rather than logging it: the use case stays free of
 * observability and the ViewModel, which owns the feature's trace, says it once. A failure
 * reported per emission would be a log line for every service the owner files.
 */
internal class ObserveCarValueUseCase(
    private val cars: CarRepository,
    private val logs: ServiceLogRepository,
    private val profiles: OwnerProfileRepository,
    private val cities: CityCatalog,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<CarValueSnapshot> = cars.observePrimaryCar().flatMapLatest { car ->
        if (car == null) return@flatMapLatest flowOf(CarValueSnapshot())
        // No estimate without a real reading. Kilometres are the second biggest term in the
        // curve after age, and a pending car's placeholder zero would value a driven car as
        // though it were showroom-fresh — the exact false precision this screen must not show.
        //
        // Reported as its own state rather than as "nothing to value": the car is there, and
        // telling its owner they have no car would be a lie about a fixable gap.
        val odometer = car.knownOdometer
            ?: return@flatMapLatest flowOf(CarValueSnapshot(odometerPending = true))
        // The catalog is read when the owner's city changes, not on every emission. Without
        // this the whole city table is re-read from storage each time a service is filed.
        val tier = profiles.observe()
            .map { it?.city }
            .distinctUntilChanged()
            .map { city -> city to resolveTier(city) }

        combine(logs.observe(car.id), tier) { entries, (cityName, resolved) ->
            CarValueSnapshot(valued = CarValued(
                car = car,
                cityName = cityName,
                cityTier = resolved,
                value = CarValueEstimator.estimate(
                    car = car,
                    odometer = odometer,
                    logs = entries,
                    cityTier = resolved.tier,
                    currentYear = clock.now().toLocalDateTime(timeZone).date.year,
                ),
            ))
        }
    }

    /**
     * The tier for a city name, and why it is missing when it is.
     *
     * A null city is not a failure: the owner may simply not have set one. The other two
     * outcomes are, and both are invisible otherwise — the estimate quietly shifts a tier
     * and the screen still looks right.
     */
    private suspend fun resolveTier(cityName: String?): CityTier {
        if (cityName == null) return CityTier.NotSet
        val catalog = runCatchingCancellableSuspend { cities.cities() }
            .getOrElse { return CityTier.Unavailable(it) }
        val tier = catalog.firstOrNull { it.name.equals(cityName, ignoreCase = true) }?.tier
        return if (tier == null) CityTier.NotListed(catalog.size) else CityTier.Resolved(tier)
    }
}

/**
 * What the city lookup came back with. The name is deliberately not carried: it is where the
 * owner lives, and nothing downstream needs it to report the outcome.
 */
internal sealed interface CityTier {

    /** The tier the curve should use, or null to fall back to the middle one. */
    val tier: Int? get() = (this as? Resolved)?.value

    data class Resolved(val value: Int) : CityTier

    /** The owner has not set a city. Not a failure. */
    data object NotSet : CityTier

    /** The catalog could not be read at all. */
    data class Unavailable(val cause: Throwable) : CityTier

    /** The catalog loaded but has no such city — it and the profile have drifted apart. */
    data class NotListed(val catalogSize: Int) : CityTier
}

/**
 * What the value screen has to work with.
 *
 * Three outcomes, not two: an estimate, no car at all, or a car whose odometer has not been
 * given yet. The last one is a gap the owner can close in one tap, and it must not be
 * reported as the first.
 */
internal data class CarValueSnapshot(
    val valued: CarValued? = null,
    val odometerPending: Boolean = false,
)

/** A car, where it lives, and what it is worth — everything the value screen renders. */
internal data class CarValued(
    val car: Car,
    val cityName: String?,
    val cityTier: CityTier,
    val value: CarValue,
)
