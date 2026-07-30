package com.hopcape.odo.core.domain.car.lookup

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Port resolving a registration number to the vehicle behind it.
 *
 * The unstable dependency (an external registry, reached through an Edge Function) behind a
 * stable contract, so onboarding can be built and tested without it — a fake returns a
 * match, another returns [DomainError.RegistrationNotFound], and neither costs a network
 * call.
 *
 * Failure is deliberately three distinct errors rather than one: "we have no record of this
 * plate" is permanent and should send the owner to manual entry, while offline and
 * service-down are worth retrying. Collapsing them would make the app tell people to give
 * up on a problem that fixes itself.
 *
 * There is no implementation in the MVP. Manual entry is the real path; a lookup that
 * *guesses* would be worse than none, because a wrong car poisons every price benchmark
 * derived from it.
 */
interface VehicleRegistryLookup {
    suspend fun lookup(registrationNumber: RegistrationNumber): Either<DomainError, RegisteredVehicle>
}
