package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import kotlinx.coroutines.flow.Flow

/**
 * Streams a car's service log (newest first) for the list screen. Thin wrapper so the
 * ViewModel drives a use case, never the repository directly (mirrors onboarding).
 */
class ObserveServiceLogsUseCase(
    private val logs: ServiceLogRepository,
) {
    operator fun invoke(carId: CarId): Flow<List<ServiceLogEntry>> = logs.observe(carId)
}
