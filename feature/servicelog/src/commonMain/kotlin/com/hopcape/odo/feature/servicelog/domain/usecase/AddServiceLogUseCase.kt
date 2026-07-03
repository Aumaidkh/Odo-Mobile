package com.hopcape.odo.feature.servicelog.domain.usecase

import arrow.core.EitherNel
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Application service for logging a service. Thin orchestrator: it mints an id,
 * delegates all field validation to [ServiceLogEntry.create], then enforces the
 * cross-entity odometer rule before handing the entry to the repository. Field
 * failures are returned all at once via [EitherNel].
 *
 * This use case is **feature-specific** (it orchestrates the service-log flow), so it
 * lives in `:feature:servicelog`, not `:core:domain` — which keeps only the shared
 * kernel (entity, value objects, [ServiceLogRepository] port, [DomainError]). The
 * clock is injected so "today" (for the future-date guard) is deterministic;
 * [timeZone] resolves the instant to a calendar date.
 */
class AddServiceLogUseCase(
    private val logs: ServiceLogRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(
        command: AddServiceLogCommand,
        carId: CarId,
        ownerId: OwnerId,
    ): EitherNel<DomainError, ServiceLogEntry> = either {
        val today = clock.now().toLocalDateTime(timeZone).date

        val entry = ServiceLogEntry.create(
            id = ServiceLogId.new(idGenerator),
            carId = carId,
            ownerId = ownerId,
            serviceDate = command.serviceDate,
            odometerKm = command.odometerKm,
            totalAmountPaise = command.totalAmountPaise,
            today = today,
            workshopName = command.workshopName,
            notes = command.notes,
        ).bind()

        // Cross-entity rule — the odometer may never move backwards. The reference
        // coalesces the car's prior logs with its onboarding reading; null means the
        // car has no baseline at all → it does not exist for this owner. Equal
        // readings are allowed (two services can share an odometer, e.g. same day).
        val previousKm = logs.mostRecentOdometerKm(carId)
        ensureNotNull(previousKm) { nonEmptyListOf(DomainError.CarNotFound) }
        ensure(entry.odometer.km >= previousKm) {
            nonEmptyListOf(
                DomainError.OdometerRegression(previousKm = previousKm, attemptedKm = entry.odometer.km),
            )
        }

        logs.add(entry).mapLeft { nonEmptyListOf(it) }.bind()
    }
}
