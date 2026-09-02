package com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase

import arrow.core.EitherNel
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.analysis.OdometerTimeline
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Application service for saving the car onboarding collected. Thin orchestrator: it mints
 * an id when there isn't one, delegates all validation to [Car.create], checks the odometer
 * against the car's own timeline, and hands the result to the repository. Returns every
 * validation failure at once via [EitherNel].
 *
 * Insert and edit are **one use case, not two**, because the flow only ever has one
 * intention — "store the car step's answers". Onboarding saves on the car step's Continue so
 * the first-scan step has a real car to hand the Bill Scanner, which means stepping back to
 * fix a detail comes through here again; whether that becomes an insert or an update is
 * bookkeeping this owns, not a decision the ViewModel should be making.
 *
 * The odometer check matters because "insert" is not the only path through here: an edit
 * (`existing != null`) can target a car id that already has real service-log history — from a
 * prior session, or pulled down by sync — and nothing else on this path stops that history
 * from being silently overwritten by a lower baseline. A genuinely new car has no history yet,
 * so [ServiceLogRepository.odometerReadings] answers `null` and the check is naturally a
 * no-op; this mirrors the check `:feature:garage`'s
 * [UpdateOdometerUseCase][com.hopcape.odo.feature.garage.domain.usecase.UpdateOdometerUseCase]
 * already does for the same field.
 *
 * This use case is **feature-specific** (car onboarding), so it lives in
 * `:feature:onboarding`, not `:core:domain` — which keeps only the shared kernel (the [Car]
 * aggregate, value objects, the [CarRepository] port, [DomainError]).
 */
internal class SaveCarUseCase(
    private val cars: CarRepository,
    private val logs: ServiceLogRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    /**
     * @param existing the id of a car this flow already stored, or `null` on the first pass.
     *  Passing it is what turns the save into an edit rather than a second car.
     */
    suspend operator fun invoke(
        command: SaveCarCommand,
        ownerId: OwnerId,
        existing: CarId? = null,
    ): EitherNel<DomainError, Car> = either {
        val id = existing ?: CarId.new(idGenerator)
        val car = Car.create(
            id = id,
            ownerId = ownerId,
            make = command.make,
            model = command.model,
            year = command.year,
            fuelType = command.fuelType,
            odometerKm = command.odometerKm,
            variant = command.variant,
            registrationNumber = command.registrationNumber,
            purchaseYear = command.purchaseYear,
            nickname = command.nickname,
            isPrimary = command.isPrimary,
        ).bind()

        // `null` means this id has no baseline at all — true of every genuinely new car,
        // since onboarding is what would have written the first one.
        val known = logs.odometerReadings(id).orEmpty()
        OdometerTimeline.validate(
            candidate = OdometerReading(
                // Not a service entry: this is the car's own baseline reading, taken today.
                logId = null,
                date = clock.now().toLocalDateTime(timeZone).date,
                odometer = car.odometer,
            ),
            known = known,
        ).mapLeft { nonEmptyListOf(it) }.bind()

        val stored = if (existing == null) cars.add(car) else cars.update(car)
        stored.mapLeft { nonEmptyListOf(it) }.bind()
    }
}
