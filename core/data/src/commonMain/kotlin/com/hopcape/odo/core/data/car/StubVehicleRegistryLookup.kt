package com.hopcape.odo.core.data.car

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.lookup.VehicleRegistryLookup
import com.hopcape.odo.core.domain.car.lookup.VehicleSource
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.model.ModelYear
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * A [VehicleRegistryLookup] backed by a handful of hardcoded plates.
 *
 * What an **unconfigured** build gets. A checkout with no Supabase credentials binds no real
 * tier (`SupabaseModule`'s `isConfigured` branch), and this keeps the "is this your car?"
 * path walkable end to end without one — the same role `FakeCarRemoteDataSource` plays for
 * the garage. The end-to-end suite drives it for the same reason: a test that reached the
 * network would be testing the network.
 *
 * It knows [KNOWN_VEHICLES] and nothing else; every other plate comes back
 * [DomainError.RegistrationNotFound]. "Not found" rather than "unavailable" is right: the
 * lookup answered, and it has no record. That is what sends the owner to manual entry
 * instead of offering a retry that would never succeed.
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
                source = VehicleSource.OWN_RECORD,
            ),
        )
    }
}
