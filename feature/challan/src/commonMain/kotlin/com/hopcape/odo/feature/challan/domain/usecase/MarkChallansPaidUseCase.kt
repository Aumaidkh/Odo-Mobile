package com.hopcape.odo.feature.challan.domain.usecase

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.challan.repository.ChallanRepository
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * "I've already paid these" — the owner's claim that every online-payable challan is
 * settled. Local-first (their screen is right immediately); the source is told
 * best-effort by the repository. Court cases are untouched.
 */
internal class MarkChallansPaidUseCase(
    private val challans: ChallanRepository,
) {

    suspend operator fun invoke(regNo: RegistrationNumber): Either<DomainError, Unit> =
        challans.markAllPendingPaid(regNo)
}
