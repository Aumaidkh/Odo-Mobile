package com.hopcape.odo.core.domain.car.lookup

/**
 * Where a [RegisteredVehicle] suggestion came from.
 *
 * The owner is told which, because the two are different claims. Their own earlier record is
 * something they wrote down; another owner's record for the same plate is a guess about a
 * car that has changed hands. Confirming the second one deserves to be a decision rather
 * than a reflex — a wrong car poisons every benchmark computed from it.
 */
enum class VehicleSource {
    /** A car this owner entered before, on this device or on their account. */
    OWN_RECORD,

    /** A car somebody else entered under the same plate. */
    ANOTHER_RECORD,
}
