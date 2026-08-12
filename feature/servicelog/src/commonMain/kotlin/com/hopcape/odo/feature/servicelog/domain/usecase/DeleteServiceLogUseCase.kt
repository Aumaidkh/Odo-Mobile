package com.hopcape.odo.feature.servicelog.domain.usecase

import arrow.core.Either
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Application service for removing a service log. A **soft** delete: the row is
 * retained (`deleted_at` stamped) so history/audit and resale checks stay intact.
 */
internal class DeleteServiceLogUseCase(
    private val logs: ServiceLogRepository,
) {
    suspend operator fun invoke(id: ServiceLogId): Either<DomainError, Unit> =
        logs.softDelete(id)
}
