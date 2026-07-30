package com.hopcape.odo.feature.onboarding.domain.usecase

import arrow.core.Either
import arrow.core.left
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.lookup.VehicleRegistryLookup
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Resolve a typed-in plate to the car behind it, so onboarding can offer "is this your
 * car?" instead of asking for make, model, year and fuel one field at a time.
 *
 * Normalizing here rather than at the call site is the point: the owner types
 * `mh 12 ab 1234`, the registry expects `MH12AB1234`, and no screen should have to know
 * that. A plate that normalizes to nothing never reaches the port — that is a wasted round
 * trip with a guaranteed answer.
 *
 * Feature-specific, so it lives in `:feature:onboarding`; it orchestrates the shared
 * `:core:domain` port and value object rather than owning either.
 */
internal class LookupPlateUseCase(
    private val registry: VehicleRegistryLookup,
) {
    suspend operator fun invoke(rawPlate: String?): Either<DomainError, RegisteredVehicle> {
        val plate = RegistrationNumber.of(rawPlate)
            ?: return DomainError.BlankRegistrationNumber.left()
        return registry.lookup(plate)
    }
}
