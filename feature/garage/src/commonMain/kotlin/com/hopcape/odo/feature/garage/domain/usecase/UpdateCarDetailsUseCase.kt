package com.hopcape.odo.feature.garage.domain.usecase

import arrow.core.EitherNel
import arrow.core.flatMap
import arrow.core.left
import arrow.core.nonEmptyListOf
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.first

/**
 * Correct what a stored car says about itself — brand, model, year, fuel, plate, nickname.
 *
 * The odometer is **not** part of this, and neither are the owner or the primary flag. The
 * reading changes through one door only ([UpdateOdometerUseCase]), which is what keeps it a
 * dated audit trail rather than another editable field; the stored values for all three are
 * read back and passed through untouched.
 *
 * Returns every validation failure at once, so an edit form can show them together.
 */
internal class UpdateCarDetailsUseCase(
    private val cars: CarRepository,
) {
    suspend operator fun invoke(
        carId: CarId,
        command: CarDetailsCommand,
    ): EitherNel<DomainError, Car> {
        val stored = cars.observe(carId).first()
            ?: return nonEmptyListOf<DomainError>(DomainError.CarNotFound).left()

        return Car.create(
            id = stored.id,
            ownerId = stored.ownerId,
            make = command.make,
            model = command.model,
            year = command.year,
            fuelType = command.fuelType,
            odometerKm = stored.odometer.km,
            variant = command.variant,
            registrationNumber = command.registrationNumber,
            purchaseYear = stored.purchaseYear?.value,
            nickname = command.nickname,
            isPrimary = stored.isPrimary,
        ).flatMap { car -> cars.update(car).mapLeft { nonEmptyListOf(it) } }
    }
}

/** Raw, unvalidated answers from the edit-car form. No odometer — see [UpdateCarDetailsUseCase]. */
internal data class CarDetailsCommand(
    val make: String?,
    val model: String?,
    val year: Int?,
    val fuelType: FuelType?,
    val variant: String? = null,
    val registrationNumber: String? = null,
    val nickname: String? = null,
)
