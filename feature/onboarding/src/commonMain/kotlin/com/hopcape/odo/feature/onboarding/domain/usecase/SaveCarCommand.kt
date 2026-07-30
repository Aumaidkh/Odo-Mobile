package com.hopcape.odo.feature.onboarding.domain.usecase

import com.hopcape.odo.core.domain.car.model.FuelType

/**
 * Raw, unvalidated car answers from the onboarding form — whichever route produced them.
 * Fields are nullable primitives; validation and normalization happen in
 * [com.hopcape.odo.core.domain.car.model.Car.create].
 */
internal data class SaveCarCommand(
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
