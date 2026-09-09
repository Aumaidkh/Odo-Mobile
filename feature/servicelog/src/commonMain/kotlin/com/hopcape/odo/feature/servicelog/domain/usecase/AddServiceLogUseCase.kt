package com.hopcape.odo.feature.servicelog.domain.usecase

import arrow.core.EitherNel
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.analysis.OdometerTimeline
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.time.Clock
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
internal class AddServiceLogUseCase(
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
            categories = command.categories,
            lineItems = command.lineItems,
            billPhotoRef = command.billPhotoRef,
        ).bind()

        // Cross-entity rule — the odometer only counts up, checked against the readings
        // around this one *in date order* so logging history backwards stays possible.
        // The readings coalesce the car's prior logs with its baseline. Empty is ordinary —
        // a car set up with the reading pending has none yet — so there is simply nothing to
        // compare against, and the entry is still validated on its own fields.
        val readings = logs.odometerReadings(carId)
        OdometerTimeline.validate(
            candidate = OdometerReading(logId = entry.id, date = entry.serviceDate, odometer = entry.odometer),
            known = readings,
        ).mapLeft { nonEmptyListOf(it) }.bind()

        logs.add(entry).mapLeft { nonEmptyListOf(it) }.bind()
    }
}
