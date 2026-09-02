package com.hopcape.odo.core.domain.car.lookup

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Port resolving a registration number to the vehicle behind it.
 *
 * The answer comes from cars Odo already holds, not from the RTO — the owner's own records
 * first, and then, when it is switched on, another owner's record for the same plate. The
 * suggestion says which (`RegisteredVehicle.source`).
 *
 * A port rather than a client call, so onboarding can be built and tested without a
 * network: a fake returns a match, another returns [DomainError.RegistrationNotFound], and
 * neither costs a round trip.
 *
 * Failure is deliberately three distinct errors rather than one: "we have no record of this
 * plate" is permanent and should send the owner to manual entry, while offline and
 * service-down are worth retrying. Collapsing them would make the app tell people to give
 * up on a problem that fixes itself.
 *
 * A match is always a suggestion the owner confirms. A wrong car accepted silently poisons
 * every price benchmark derived from it.
 */
interface VehicleRegistryLookup {
    suspend fun lookup(registrationNumber: RegistrationNumber): Either<DomainError, RegisteredVehicle>
}
