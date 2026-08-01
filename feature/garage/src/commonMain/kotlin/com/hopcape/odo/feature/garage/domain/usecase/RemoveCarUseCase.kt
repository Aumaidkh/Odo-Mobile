package com.hopcape.odo.feature.garage.domain.usecase

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Take a car out of the garage, together with its service history and its documents.
 *
 * A soft delete: the rows stay as tombstones so the removal itself can reach the server,
 * and the repository does all three in one write. What the owner is giving up is spelled
 * out on the confirmation sheet first, from the garage snapshot's own counts.
 */
internal class RemoveCarUseCase(
    private val cars: CarRepository,
) {
    suspend operator fun invoke(carId: CarId): Either<DomainError, Unit> = cars.softDelete(carId)
}
