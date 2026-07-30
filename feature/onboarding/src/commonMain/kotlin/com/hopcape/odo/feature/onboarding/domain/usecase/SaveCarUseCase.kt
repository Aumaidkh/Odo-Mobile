package com.hopcape.odo.feature.onboarding.domain.usecase

import arrow.core.EitherNel
import arrow.core.flatMap
import arrow.core.nonEmptyListOf
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Application service for saving the car onboarding collected. Thin orchestrator: it mints
 * an id when there isn't one, delegates all validation to [Car.create], and hands the result
 * to the repository. Returns every validation failure at once via [EitherNel].
 *
 * Insert and edit are **one use case, not two**, because the flow only ever has one
 * intention — "store the car step's answers". Onboarding saves on the car step's Continue so
 * the first-scan step has a real car to hand the Bill Scanner, which means stepping back to
 * fix a detail comes through here again; whether that becomes an insert or an update is
 * bookkeeping this owns, not a decision the ViewModel should be making.
 *
 * This use case is **feature-specific** (car onboarding), so it lives in
 * `:feature:onboarding`, not `:core:domain` — which keeps only the shared kernel (the [Car]
 * aggregate, value objects, the [CarRepository] port, [DomainError]).
 */
internal class SaveCarUseCase(
    private val cars: CarRepository,
    private val idGenerator: IdGenerator,
) {
    /**
     * @param existing the id of a car this flow already stored, or `null` on the first pass.
     *  Passing it is what turns the save into an edit rather than a second car.
     */
    suspend operator fun invoke(
        command: SaveCarCommand,
        ownerId: OwnerId,
        existing: CarId? = null,
    ): EitherNel<DomainError, Car> =
        Car.create(
            id = existing ?: CarId.new(idGenerator),
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
        ).flatMap { car ->
            val stored = if (existing == null) cars.add(car) else cars.update(car)
            stored.mapLeft { nonEmptyListOf(it) }
        }
}
