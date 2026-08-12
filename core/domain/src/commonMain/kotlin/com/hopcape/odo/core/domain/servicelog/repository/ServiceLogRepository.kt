package com.hopcape.odo.core.domain.servicelog.repository

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
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
     * Every known odometer reading for the car — its non-deleted logs plus the onboarding
     * baseline — for the ordering check in
     * [OdometerTimeline][com.hopcape.odo.core.domain.servicelog.analysis.OdometerTimeline].
     *
     * The whole timeline rather than just the highest reading, because the rule is
     * date-relative: a backdated service is checked against its neighbours, which the
     * maximum alone cannot answer. Order is not guaranteed — the timeline sorts what it
     * needs.
     *
     * `null` means the car has no baseline at all (it does not exist for the owner); an
     * empty list would mean a car with no readings, which onboarding cannot produce.
     */
    suspend fun odometerReadings(carId: CarId): List<OdometerReading>?

    /**
     * The same readings as [odometerReadings], as a stream — for screens that stay open
     * while the timeline changes underneath them.
     *
     * The cost tracker is the reason it exists: distance driven comes from these readings,
     * and the car's own reading moves from the garage, not from a service log. Re-reading
     * only when the logs change would leave a stale ₹/km on screen after the odometer was
     * updated. Emits an empty list for a car that does not exist, since a stream has no
     * "not found" to report and nothing to compute from either way.
     */
    fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>>
}
