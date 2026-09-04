package com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase

import arrow.core.EitherNel
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.analysis.OdometerTimeline
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Stores the last service the owner remembers as a [LogSource.DECLARED] log row.
 *
 * Nothing holds a "last service" on its own — the health score, the reminders and the
 * pre-service checklist all read the newest entry — so the setup answer has to become one.
 * It carries a date and a reading and no money, which is what `DECLARED` means and why
 * `RunningCostCalculator` leaves it out.
 *
 * The reading is checked against the car's timeline before it is written: setup has already
 * stored today's odometer, so a remembered reading higher than it is the one mistake this
 * step can make, and it would poison every distance the app computes afterwards.
 */
internal class RecordDeclaredServiceUseCase(
    private val logs: ServiceLogRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(
        carId: CarId,
        ownerId: OwnerId,
        date: LocalDate,
        odometerKm: Int,
    ): EitherNel<DomainError, ServiceLogEntry> = either {
        val id = ServiceLogId.new(idGenerator)
        val today = clock.now().toLocalDateTime(timeZone).date
        val entry = ServiceLogEntry.create(
            id = id,
            carId = carId,
            ownerId = ownerId,
            serviceDate = date,
            odometerKm = odometerKm,
            // No bill, so no total. Zero here is the truth, not a placeholder.
            totalAmountPaise = null,
            today = today,
            source = LogSource.DECLARED,
        ).bind()

        val known = logs.odometerReadings(carId).orEmpty()
        OdometerTimeline.validate(
            candidate = OdometerReading(logId = id, date = date, odometer = entry.odometer),
            known = known,
        ).mapLeft { nonEmptyListOf(it) }.bind()

        logs.add(entry).mapLeft { nonEmptyListOf(it) }.bind()
    }
}
