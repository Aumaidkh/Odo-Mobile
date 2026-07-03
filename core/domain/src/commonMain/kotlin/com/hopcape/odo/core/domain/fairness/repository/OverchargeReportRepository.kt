package com.hopcape.odo.core.domain.fairness.repository

import arrow.core.Either
import com.hopcape.odo.core.domain.fairness.model.OverchargeReport
import com.hopcape.odo.core.domain.shared.DomainError

/** Port for submitting an [OverchargeReport] (to the server / support). */
interface OverchargeReportRepository {
    suspend fun submit(report: OverchargeReport): Either<DomainError, Unit>
}
