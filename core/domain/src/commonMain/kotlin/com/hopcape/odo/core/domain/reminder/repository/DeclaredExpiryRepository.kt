package com.hopcape.odo.core.domain.reminder.repository

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.reminder.model.DeclaredExpiry
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow

/** Expiry dates declared without a document. Stored in the reminders table and synced. */
interface DeclaredExpiryRepository {

    fun observe(carId: CarId): Flow<List<DeclaredExpiry>>

    /** Stores [expiry], replacing any earlier one of the same kind for the car. */
    suspend fun declare(expiry: DeclaredExpiry): Either<DomainError, Unit>
}
