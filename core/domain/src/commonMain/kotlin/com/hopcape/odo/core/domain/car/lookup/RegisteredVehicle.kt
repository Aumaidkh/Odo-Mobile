package com.hopcape.odo.core.domain.car.lookup

import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.model.ModelYear

/**
 * What a registry knows about a plate — the "is this your car?" suggestion, before the
 * owner has confirmed anything.
 *
 * Deliberately *not* a [com.hopcape.odo.core.domain.car.model.Car]: this is an unconfirmed
 * claim from an outside system, and a Car is something the owner vouches for. Keeping them
 * separate is what stops a wrong match from quietly becoming the car every fairness
 * benchmark and health score is computed against.
 *
 * The odometer is absent on purpose — no registry knows it, and Odo cannot infer it. It is
 * always asked for.
 */
data class RegisteredVehicle(
    val make: String,
    val model: String,
    val variant: String?,
    val year: ModelYear,
    val fuelType: FuelType,
)
