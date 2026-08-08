package com.hopcape.odo.feature.autoodometer.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.odometer.CurrentOdometerProvider
import com.hopcape.odo.core.domain.servicelog.policy.ServiceDueStatus
import com.hopcape.odo.core.domain.servicelog.policy.ServiceIntervalPolicy
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The trip-logged screen's (M6) "SERVICE DUE" nudge — where the car stands against its
 * service interval, reused rather than reinvented.
 *
 * [ServiceIntervalPolicy] already lives in `:core:domain` as the one shared answer to
 * "when is this car's next service due" — the reminders feed
 * ([ObserveRemindersUseCase][com.hopcape.odo.feature.reminders.domain.usecase.ObserveRemindersUseCase])
 * calls the exact same function. That is the golden-rule case in practice: two features
 * needing the "same" use case each write their own against the shared `:core:domain`
 * port, instead of one feature importing the other's. No new port was needed here.
 *
 * Returns the raw [ServiceDueStatus] rather than a feature-local wrapper — the
 * trip-logged ViewModel (F7) decides whether [ServiceDueStatus.NeverServiced] hides the
 * nudge row and writes its own "Oil change in about 1,800 km" copy from the km/day
 * numbers each case already carries.
 */
internal class ObserveServiceDueNudge(
    private val serviceLogs: ServiceLogRepository,
    private val currentOdometer: CurrentOdometerProvider,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    operator fun invoke(carId: CarId): Flow<ServiceDueStatus> = combine(
        serviceLogs.observe(carId),
        currentOdometer.observeCurrent(carId),
    ) { entries, odometer ->
        val today = clock.now().toLocalDateTime(timeZone).date
        val last = entries.maxWithOrNull(compareBy({ it.serviceDate }, { it.odometer.km }))
        ServiceIntervalPolicy.statusFor(
            lastServiceDate = last?.serviceDate,
            lastServiceOdometer = last?.odometer,
            currentOdometer = odometer,
            today = today,
        )
    }
}
