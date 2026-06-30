package com.hopcape.odo.core.domain.car.usecase

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
 * Application service for adding a car. Thin orchestrator: it mints an id,
 * delegates all validation to [Car.create], and on success hands the car to the
 * repository. Returns every validation failure at once via [EitherNel].
 */
class AddCarUseCase(
    private val cars: CarRepository,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(
        command: AddCarCommand,
        ownerId: OwnerId,
    ): EitherNel<DomainError, Car> =
        Car.create(
            id = CarId.new(idGenerator),
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
        ).flatMap { car -> cars.add(car).mapLeft { nonEmptyListOf(it) } }
}
