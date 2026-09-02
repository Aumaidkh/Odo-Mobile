package com.hopcape.odo.core.data.car

import arrow.core.Either
import arrow.core.left
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.lookup.VehicleRegistryLookup
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Asks each lookup in turn and takes the first one that names a car.
 *
 * The order is cheapest and most trustworthy first: the cars on this device, then the
 * owner's own cars on the server, then — only when `plate_lookup_enabled` is on — a car
 * some other owner entered under the same plate.
 *
 * Only [DomainError.RegistrationNotFound] falls through to the next tier. A tier that was
 * offline or unwell has not said the plate is unknown, and continuing past it would turn a
 * retryable problem into a permanent "we have no record" that sends the owner to manual
 * entry for good. That failure is carried and reported if no later tier does better.
 */
internal class ChainedVehicleRegistryLookup(
    private val tiers: List<VehicleRegistryLookup>,
) : VehicleRegistryLookup {

    override suspend fun lookup(
        registrationNumber: RegistrationNumber,
    ): Either<DomainError, RegisteredVehicle> {
        var failure: DomainError = DomainError.RegistrationNotFound
        for (tier in tiers) {
            val answer = tier.lookup(registrationNumber)
            answer.fold(
                ifLeft = { error ->
                    // The first real failure is the one worth reporting: it happened at the
                    // cheapest tier that could still have answered.
                    if (error != DomainError.RegistrationNotFound &&
                        failure == DomainError.RegistrationNotFound
                    ) {
                        failure = error
                    }
                },
                ifRight = { return answer },
            )
        }
        return failure.left()
    }
}
