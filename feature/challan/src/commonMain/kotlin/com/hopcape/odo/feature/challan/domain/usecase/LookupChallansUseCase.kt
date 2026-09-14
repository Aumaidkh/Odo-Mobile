package com.hopcape.odo.feature.challan.domain.usecase

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.challan.model.ChallanLookup
import com.hopcape.odo.core.domain.challan.repository.ChallanRepository
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * A buyer's one-off check of an arbitrary plate — remote-only, shown once, saved nowhere
 * (the lookup screen's privacy card is a promise this use case keeps by construction:
 * there is no write anywhere on this path).
 */
internal class LookupChallansUseCase(
    private val challans: ChallanRepository,
) {

    suspend operator fun invoke(regNo: RegistrationNumber): Either<DomainError, ChallanLookup> =
        challans.lookup(regNo)
}
