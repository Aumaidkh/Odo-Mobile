package com.hopcape.odo.feature.garage.domain.usecase

import arrow.core.EitherNel
import arrow.core.flatMap
import arrow.core.nonEmptyListOf
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Put a car in the garage. Thin orchestrator: it mints an id, leaves every rule to
 * [Car.create], and hands the result to the repository. Returns all validation failures at
 * once so the form can show them together.
 *
 * The car is stored as the primary one. The MVP garage holds a single car, so the car the
 * owner just added is the car every other screen is about; multi-car (Phase 2) makes that a
 * real choice and this is where it changes.
 *
 * Feature-specific, so it lives here rather than in `:core:domain`, and it is the garage's
 * own — onboarding has its own save for its own flow, and one feature never imports another.
 */
internal class AddCarUseCase(
    private val cars: CarRepository,
    private val idGenerator: IdGenerator,
    private val owner: CurrentOwnerProvider,
) {
    suspend operator fun invoke(command: AddCarCommand): EitherNel<DomainError, Car> =
        Car.create(
            id = CarId.new(idGenerator),
            ownerId = owner.currentOwnerId(),
            make = command.make,
            model = command.model,
            year = command.year,
            fuelType = command.fuelType,
            odometerKm = command.odometerKm,
            variant = command.variant,
            registrationNumber = command.registrationNumber,
            nickname = command.nickname,
            isPrimary = true,
        ).flatMap { car -> cars.add(car).mapLeft { nonEmptyListOf(it) } }
}

/**
 * Raw, unvalidated answers from the add-car form. Nullable primitives; validation and
 * normalization happen in [Car.create].
 *
 * The odometer is here and mandatory: it is the number ₹/km, the health score and every
 * km-anomaly check are built on, so a car enters the garage with one.
 */
internal data class AddCarCommand(
    val make: String?,
    val model: String?,
    val year: Int?,
    val fuelType: FuelType?,
    val odometerKm: Int?,
    val variant: String? = null,
    val registrationNumber: String? = null,
    val nickname: String? = null,
)
