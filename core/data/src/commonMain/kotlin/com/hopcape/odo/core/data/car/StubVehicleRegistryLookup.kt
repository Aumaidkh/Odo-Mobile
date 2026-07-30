package com.hopcape.odo.core.data.car

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.lookup.VehicleRegistryLookup
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.model.ModelYear
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * A development [VehicleRegistryLookup] backed by a handful of hardcoded plates, so the
 * "is this your car?" path can be walked end to end before any registry integration exists.
 *
 * **This must not ship.** It is here to exercise the flow, not to answer questions about
 * real cars: it knows [KNOWN_VEHICLES] and nothing else, and every other plate comes back
 * [DomainError.RegistrationNotFound]. Replacing it is one line in `coreDataModule` — no
 * caller changes, which is the point of the port.
 *
 * "Not found" rather than "unavailable" is the right failure for an unknown plate here: the
 * lookup did answer, and it has no record. That is the answer that sends the owner to manual
 * entry instead of offering a retry that would never succeed.
 */
internal class StubVehicleRegistryLookup : VehicleRegistryLookup {

    override suspend fun lookup(
        registrationNumber: RegistrationNumber,
    ): Either<DomainError, RegisteredVehicle> =
        KNOWN_VEHICLES[registrationNumber.value]?.right()
            ?: DomainError.RegistrationNotFound.left()

    private companion object {
        /**
         * Keyed on the **normalized** plate (uppercase, no spaces), the same form
         * [RegistrationNumber] stores — so `jk03n 3078` and `JK03N3078` both resolve.
         *
         * The car is a real entry from the seeded catalog (make, model and trim all exist
         * in `VEHICLE_SEED`), so confirming the match and then editing it lands on values
         * the pickers actually offer.
         */
        val KNOWN_VEHICLES: Map<String, RegisteredVehicle> = mapOf(
            "JK03N3078" to RegisteredVehicle(
                make = "Maruti Suzuki",
                model = "Swift",
                variant = "VXI",
                year = ModelYear.of(2020).getOrNull()!!,
                fuelType = FuelType.PETROL,
            ),
        )
    }
}
