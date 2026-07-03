package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import kotlinx.coroutines.flow.Flow

/**
 * Streams a single entry for the detail / edit-prefill screens; emits `null` if the
 * entry is missing or was deleted.
 */
class GetServiceLogUseCase(
    private val logs: ServiceLogRepository,
) {
    operator fun invoke(id: ServiceLogId): Flow<ServiceLogEntry?> = logs.observe(id)
}
