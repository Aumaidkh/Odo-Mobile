package com.hopcape.odo.core.domain.car.usecase

import com.hopcape.odo.core.domain.car.model.FuelType

/**
 * Raw, unvalidated onboarding input from the presentation layer. Fields are
 * nullable primitives; validation/normalization happens in [com.hopcape.odo.core.domain.car.model.Car.create].
 */
data class AddCarCommand(
    val make: String?,
    val model: String?,
    val year: Int?,
    val fuelType: FuelType?,
    val odometerKm: Int?,
    val variant: String? = null,
    val registrationNumber: String? = null,
    val purchaseYear: Int? = null,
    val nickname: String? = null,
    val isPrimary: Boolean = false,
)
