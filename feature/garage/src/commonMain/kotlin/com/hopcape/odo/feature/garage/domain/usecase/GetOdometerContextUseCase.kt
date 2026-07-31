package com.hopcape.odo.feature.garage.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * What the update-odometer sheet needs before the owner touches the drum: the reading
 * already on record and the day it was written down.
 *
 * The car alone cannot answer this. It carries the reading but not its date, and the date
 * is the whole point — "+5,000 km since March" is useful, "+5,000 km since some earlier
 * moment" is not.
 *
 * The sheet works out how far the car has come and roughly how far it goes in a month from
 * these two facts and whatever the owner dials in, so nothing here is computed against a
 * number the owner has not chosen yet.
 */
internal class GetOdometerContextUseCase(
    private val logs: ServiceLogRepository,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(carId: CarId): Either<DomainError, OdometerContext> = either {
        val readings = ensureNotNull(logs.odometerReadings(carId)) { DomainError.CarNotFound }
        // The car's own reading is the one without a log id. A live car always has it, so
        // its absence means the readings came from somewhere they should not have.
        val current = ensureNotNull(readings.firstOrNull { it.logId == null }) { DomainError.CarNotFound }

        OdometerContext(
            lastRecorded = current,
            today = clock.now().toLocalDateTime(timeZone).date,
        )
    }
}

/** The reading the sheet starts from, and the day it is being changed on. */
internal data class OdometerContext(
    /** What the car reads on record, and the day that reading was written down. */
    val lastRecorded: OdometerReading,
    val today: LocalDate,
) {
    /** How far the car has come since [lastRecorded], for a reading of [km]. Never negative. */
    fun kmSinceLastRecorded(km: Int): Int = (km - lastRecorded.odometer.km).coerceAtLeast(0)

    /**
     * Roughly how far the car goes in a month, from the distance covered since
     * [lastRecorded] — or `null` when too little time has passed to say anything.
     *
     * Below [MIN_DAYS_FOR_RATE] days the arithmetic turns a few days of driving into a
     * confident monthly figure, which is a made-up number wearing a real one's clothes.
     */
    fun kmPerMonth(km: Int): Int? {
        val days = lastRecorded.date.daysUntil(today)
        if (days < MIN_DAYS_FOR_RATE) return null
        return (kmSinceLastRecorded(km).toDouble() * DAYS_PER_MONTH / days).toInt()
    }

    private companion object {
        const val MIN_DAYS_FOR_RATE = 14
        const val DAYS_PER_MONTH = 30.0
    }
}
