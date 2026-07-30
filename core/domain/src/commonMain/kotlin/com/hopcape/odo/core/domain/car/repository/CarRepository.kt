package com.hopcape.odo.core.domain.car.repository

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow

/**
 * Port for persisting and observing cars. The implementation lives in
 * `:core:data` (local DB as source of truth); the domain stays ignorant of it.
 *
 * "Exactly one primary car per owner" is a cross-aggregate invariant, so it is
 * enforced by the implementation + DB unique index (`uq_cars_one_primary`),
 * not inside the [Car] aggregate.
 */
interface CarRepository {
    suspend fun add(car: Car): Either<DomainError, Car>

    /**
     * Persist changes to a car that already exists.
     *
     * Onboarding saves the car when its step completes rather than at the end of the flow,
     * so that the first-scan step has a real car to hand the Bill Scanner. Stepping back to
     * correct a detail therefore edits a stored car — an update, not a second insert.
     *
     * Returns [DomainError.CarNotFound] if no live car has this id.
     */
    suspend fun update(car: Car): Either<DomainError, Car>

    fun observePrimaryCar(): Flow<Car?>
}
