package com.hopcape.odo.feature.servicelog.domain.usecase

import arrow.core.EitherNel
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Application service for editing an existing service log. Re-runs the full field
 * validation (reusing [ServiceLogEntry.create] with the existing id) and re-checks
 * the odometer ordering, since an edit can move either the date or the reading.
 *
 * The ordering reference includes all of the car's readings, so an unchanged
 * odometer passes (equal is allowed); an edit that pushes it below a known reading
 * is rejected with [DomainError.OdometerRegression].
 */
class UpdateServiceLogUseCase(
    private val logs: ServiceLogRepository,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(
        command: UpdateServiceLogCommand,
    ): EitherNel<DomainError, ServiceLogEntry> = either {
        val today = clock.now().toLocalDateTime(timeZone).date

        val entry = ServiceLogEntry.create(
            id = command.id,
            carId = command.carId,
            ownerId = command.ownerId,
            serviceDate = command.serviceDate,
            odometerKm = command.odometerKm,
            totalAmountPaise = command.totalAmountPaise,
            today = today,
            workshopName = command.workshopName,
            notes = command.notes,
            categories = command.categories,
            lineItems = command.lineItems,
            billPhotoRef = command.billPhotoRef,
        ).bind()

        val previousKm = logs.mostRecentOdometerKm(command.carId)
        ensureNotNull(previousKm) { nonEmptyListOf(DomainError.CarNotFound) }
        ensure(entry.odometer.km >= previousKm) {
            nonEmptyListOf(
                DomainError.OdometerRegression(previousKm = previousKm, attemptedKm = entry.odometer.km),
            )
        }

        logs.update(entry).mapLeft { nonEmptyListOf(it) }.bind()
    }
}
