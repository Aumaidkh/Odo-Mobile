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

    fun observePrimaryCar(): Flow<Car?>
}
