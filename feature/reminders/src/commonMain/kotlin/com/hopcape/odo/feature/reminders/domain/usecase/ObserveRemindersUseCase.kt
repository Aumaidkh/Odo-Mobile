package com.hopcape.odo.feature.reminders.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.odometer.CurrentOdometerProvider
import com.hopcape.odo.core.domain.reminder.analysis.ReminderFeedPolicy
import com.hopcape.odo.core.domain.reminder.model.ReminderFeed
import com.hopcape.odo.core.domain.reminder.repository.ReminderRepository
import com.hopcape.odo.core.domain.servicelog.policy.ServiceIntervalPolicy
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Builds the reminders screen's whole answer — this week, upcoming, suggestions — from
 * one combined read of everything the feed derives from.
 *
 * One stream instead of a flow per section, for the same reason the vault reads once:
 * separate reads emit at different times, and a header that says "2 need attention"
 * must never sit above a list that disagrees. Every emission is resolved against the
 * same day, read once.
 */
internal class ObserveRemindersUseCase(
    private val documents: DocumentRepository,
    private val serviceLogs: ServiceLogRepository,
    private val reminders: ReminderRepository,
    private val currentOdometer: CurrentOdometerProvider,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    operator fun invoke(carId: CarId): Flow<ReminderFeed> = combine(
        documents.observe(carId),
        serviceLogs.observe(carId),
        currentOdometer.observeCurrent(carId),
        reminders.observeCustom(carId),
        reminders.observeDismissals(carId),
    ) { docs, entries, odometer, customs, dismissals ->
        val today = clock.now().toLocalDateTime(timeZone).date
        val last = entries.maxWithOrNull(compareBy({ it.serviceDate }, { it.odometer.km }))
        ReminderFeedPolicy.feedFor(
            documents = docs,
            serviceStatus = ServiceIntervalPolicy.statusFor(
                lastServiceDate = last?.serviceDate,
                lastServiceOdometer = last?.odometer,
                currentOdometer = odometer,
                today = today,
            ),
            customs = customs,
            dismissals = dismissals,
            today = today,
            currentKm = odometer?.km,
        )
    }
}
