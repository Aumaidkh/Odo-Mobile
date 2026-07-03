package com.hopcape.odo.feature.servicelog.domain.usecase

import arrow.core.Either
import com.hopcape.odo.core.domain.fairness.model.OverchargeReport
import com.hopcape.odo.core.domain.fairness.repository.OverchargeReportRepository
import com.hopcape.odo.core.domain.shared.DomainError

/** Submits a user's "Report this overcharge" for a flagged entry. */
class ReportOverchargeUseCase(
    private val reports: OverchargeReportRepository,
) {
    suspend operator fun invoke(report: OverchargeReport): Either<DomainError, Unit> = reports.submit(report)
}
