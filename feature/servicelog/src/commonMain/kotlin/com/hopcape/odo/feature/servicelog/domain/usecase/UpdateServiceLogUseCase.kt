package com.hopcape.odo.feature.servicelog.domain.usecase

import arrow.core.EitherNel
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.hopcape.odo.core.domain.servicelog.analysis.OdometerTimeline
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Application service for editing an existing service log. Re-runs the full field
 * validation (reusing [ServiceLogEntry.create] with the existing id) and re-checks
 * the odometer ordering, since an edit can move either the date or the reading.
 *
 * The ordering check runs over the car's timeline with **this entry excluded**, so an
 * unchanged reading always passes and correcting the entry's own value is a typo fix, not
 * a regression against itself. Only a value that crosses a *neighbouring* entry is
 * rejected — below the one before it ([DomainError.OdometerRegression]) or above the one
 * after it ([DomainError.OdometerAheadOfLaterEntry]).
 */
internal class UpdateServiceLogUseCase(
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

        // Re-checked against the car's timeline with **this entry excluded**, so correcting
        // its own reading is a typo fix rather than a regression against its stored value.
        val readings = logs.odometerReadings(command.carId)
        ensureNotNull(readings) { nonEmptyListOf(DomainError.CarNotFound) }
        OdometerTimeline.validate(
            candidate = OdometerReading(logId = entry.id, date = entry.serviceDate, odometer = entry.odometer),
            known = readings,
        ).mapLeft { nonEmptyListOf(it) }.bind()

        logs.update(entry).mapLeft { nonEmptyListOf(it) }.bind()
    }
}
