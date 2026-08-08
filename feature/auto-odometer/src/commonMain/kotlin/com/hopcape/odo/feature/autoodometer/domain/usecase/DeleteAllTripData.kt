package com.hopcape.odo.feature.autoodometer.domain.usecase

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.trip.repository.TripRepository

/** Settings' "Delete all trip data" (behind a typed confirm dialog in the ViewModel, F8). */
internal class DeleteAllTripData(
    private val trips: TripRepository,
) {
    suspend operator fun invoke(carId: CarId): Either<DomainError, Unit> = trips.deleteAllForCar(carId)
}
