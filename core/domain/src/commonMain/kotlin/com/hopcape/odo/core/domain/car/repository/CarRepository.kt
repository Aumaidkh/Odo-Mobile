package com.hopcape.odo.core.domain.car.repository

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.owner.model.OwnerId
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

    /**
     * The live car [ownerId] already has on file under [registrationNumber], if any.
     *
     * What lets "add a car" recognize a plate it already has and become an update instead
     * of colliding with the DB's own `uq_cars_owner_reg` unique index — a plate identifies
     * one physical car, so a second one under the same owner is never a genuinely new car.
     */
    suspend fun findByRegistration(ownerId: OwnerId, registrationNumber: RegistrationNumber): Car?

    fun observePrimaryCar(): Flow<Car?>

    /**
     * A single live car; emits `null` if there is no such car.
     *
     * Separate from [observePrimaryCar] because a screen that was told which car it is
     * about must read that car, not whichever one is primary. The garage names its car
     * through `ActiveCarProvider`, and re-resolving "primary" underneath would make the
     * screen quietly follow a different car than the one it was opened for.
     */
    fun observe(id: CarId): Flow<Car?>

    /**
     * Soft delete a car and everything scoped to it — its service logs and its documents —
     * as one write.
     *
     * The children go with the car because they are only reachable through it. A log or a
     * document left behind is invisible to every screen but still counts: the free tier's
     * document cap is per owner, so an orphaned document would keep using up an allowance
     * for a car that is gone.
     *
     * Deleting a car that is already gone succeeds. The caller asked for it to not exist,
     * and it does not.
     */
    suspend fun softDelete(id: CarId): Either<DomainError, Unit>
}
