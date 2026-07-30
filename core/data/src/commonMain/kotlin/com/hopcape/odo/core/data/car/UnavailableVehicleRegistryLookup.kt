package com.hopcape.odo.core.data.car

import arrow.core.Either
import arrow.core.left
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.lookup.VehicleRegistryLookup
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * The MVP's [VehicleRegistryLookup]: there is no registry service, so it says so.
 *
 * This is the honest answer, not a placeholder to be improved on the way to launch. No
 * registry integration is in MVP scope, and a lookup that *guessed* — nearest match, most
 * common car for the state code — would be far worse than none: the guessed car silently
 * becomes the one every fairness benchmark, per-km figure and health score is computed
 * against, and the owner has no reason to doubt a screen that filled itself in.
 *
 * [DomainError.LookupUnavailable] (rather than [DomainError.RegistrationNotFound]) is the
 * truthful failure: nothing was asked, so nothing was "not found". It also keeps the retry
 * affordance sensible for the day a real adapter replaces this one — at which point only
 * the Koin binding changes.
 */
internal class UnavailableVehicleRegistryLookup : VehicleRegistryLookup {
    override suspend fun lookup(
        registrationNumber: RegistrationNumber,
    ): Either<DomainError, RegisteredVehicle> = DomainError.LookupUnavailable.left()
}
