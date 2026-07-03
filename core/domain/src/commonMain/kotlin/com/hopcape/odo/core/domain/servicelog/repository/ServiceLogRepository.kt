package com.hopcape.odo.core.domain.servicelog.repository

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow

/**
 * Port for persisting and observing a car's service logs. The implementation lives
 * in `:core:data` (local DB as source of truth); the domain stays ignorant of it.
 */
interface ServiceLogRepository {

    /** A car's non-deleted logs, newest first. */
    fun observe(carId: CarId): Flow<List<ServiceLogEntry>>

    /** A single non-deleted entry (detail / edit-prefill); emits `null` if absent. */
    fun observe(id: ServiceLogId): Flow<ServiceLogEntry?>

    suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry>

    suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry>

    /** Soft delete (sets `deleted_at`); the row is retained for history/audit. */
    suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit>

    /**
     * The reference reading for the backwards-progression check: the highest known
     * odometer for the car — coalescing its prior logs with its onboarding reading.
     * `null` means the car has no baseline at all (it does not exist for the owner).
     */
    suspend fun mostRecentOdometerKm(carId: CarId): Int?
}
