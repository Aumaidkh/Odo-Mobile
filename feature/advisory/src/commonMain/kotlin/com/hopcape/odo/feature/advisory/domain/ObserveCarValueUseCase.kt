package com.hopcape.odo.feature.advisory.domain

import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.city.CityCatalog
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.feature.advisory.presentation.AdvisoryTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 */
internal class ObserveCarValueUseCase(
    private val cars: CarRepository,
    private val logs: ServiceLogRepository,
    private val profiles: OwnerProfileRepository,
    private val cities: CityCatalog,
    private val clock: Clock,
    private val telemetry: AdvisoryTelemetry,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<CarValued?> = cars.observePrimaryCar().flatMapLatest { car ->
        if (car == null) return@flatMapLatest flowOf(null)
        combine(logs.observe(car.id), profiles.observe()) { entries, profile ->
            CarValued(
                car = car,
                cityName = profile?.city,
                value = CarValueEstimator.estimate(
                    car = car,
                    logs = entries,
                    cityTier = tierOf(profile?.city),
                    currentYear = clock.now().toLocalDateTime(timeZone).date.year,
                ),
            )
        }
    }

    /**
     * The tier for a city name, or null when it cannot be resolved.
     *
     * Null is a real answer, not a failure: the owner may not have set a city, and the
     * catalog is synced rather than bundled so it can be empty on a fresh install. The
     * curve treats an unknown tier as the middle one.
     *
     * The two ways it *is* a failure are reported, because both are invisible otherwise —
     * the estimate simply shifts a tier and the screen still looks right. Neither report
     * carries the city name: it is where the owner lives.
     */
    private suspend fun tierOf(cityName: String?): Int? {
        if (cityName == null) return null
        val catalog = runCatchingCancellableSuspend { cities.cities() }
            .onFailure(telemetry::cityCatalogUnavailable)
            .getOrNull()
            ?: return null
        return catalog.firstOrNull { it.name.equals(cityName, ignoreCase = true) }?.tier
            ?: run { telemetry.cityNotListed(catalog.size); null }
    }
}

/** A car, where it lives, and what it is worth — everything the value screen renders. */
internal data class CarValued(
    val car: Car,
    val cityName: String?,
    val value: CarValue,
)
