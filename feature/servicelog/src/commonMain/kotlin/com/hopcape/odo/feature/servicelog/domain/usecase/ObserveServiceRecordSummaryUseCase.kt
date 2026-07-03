package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.model.ServiceRecordSummary
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Streams the aggregate [ServiceRecordSummary] for a car (total spent, counts,
 * verified ratio, strength) — the one honest source behind both the Ledger stat card
 * and the Timeline record ring.
 */
class ObserveServiceRecordSummaryUseCase(
    private val logs: ServiceLogRepository,
) {
    operator fun invoke(carId: CarId): Flow<ServiceRecordSummary> =
        logs.observe(carId).map(ServiceRecordSummary::of)
}
