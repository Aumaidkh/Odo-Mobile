package com.hopcape.odo.core.domain.car.model

import arrow.core.EitherNel
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import arrow.core.raise.zipOrAccumulate
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.shared.OdometerReading

/**
 * The Car aggregate root — the consistency boundary for a single car.
 *
 * Construct only via [create], which enforces every invariant and accumulates
 * *all* validation failures (so an onboarding form can show them at once). The
 * private constructor guarantees an invalid [Car] can never exist.
 *
 * The cross-aggregate "one primary car per owner" rule is NOT enforced here —
 * it spans multiple Car instances and lives in the repository/DB.
 */
class Car private constructor(
    val id: CarId,
    val ownerId: OwnerId,
    val make: String,
    val model: String,
    val variant: String?,
    val year: ModelYear,
    val fuelType: FuelType,
    val registrationNumber: RegistrationNumber?,
    val odometer: OdometerReading,
    val purchaseYear: PurchaseYear?,
    val nickname: String?,
    val isPrimary: Boolean,
) {
    companion object {
        /**
         * Validating factory — the single entry point for building a [Car].
         * Takes raw, domain-meaningful inputs (not a use-case command) so any
         * use case can construct a car without the entity depending outward.
         * Accumulates every validation failure.
         */
        fun create(
            id: CarId,
            ownerId: OwnerId,
            make: String?,
            model: String?,
            year: Int?,
            fuelType: FuelType?,
            odometerKm: Int?,
            variant: String? = null,
            registrationNumber: String? = null,
            purchaseYear: Int? = null,
            nickname: String? = null,
            isPrimary: Boolean = false,
        ): EitherNel<DomainError, Car> = either {
            zipOrAccumulate(
                { ensureNotNull(make?.trim()?.ifBlank { null }) { DomainError.BlankMake } },
                { ensureNotNull(model?.trim()?.ifBlank { null }) { DomainError.BlankModel } },
                { ModelYear.of(year).bind() },
                { ensureNotNull(fuelType) { DomainError.MissingFuelType } },
                { OdometerReading.of(odometerKm).bind() },
                { PurchaseYear.of(purchaseYear).bind() },
                { RegistrationNumber.of(registrationNumber) },
            ) { validMake, validModel, validYear, validFuel, validOdometer, validPurchaseYear, validRegistration ->
                Car(
                    id = id,
                    ownerId = ownerId,
                    make = validMake,
                    model = validModel,
                    variant = variant?.trim()?.ifBlank { null },
                    year = validYear,
                    fuelType = validFuel,
                    registrationNumber = validRegistration,
                    odometer = validOdometer,
                    purchaseYear = validPurchaseYear,
                    nickname = nickname?.trim()?.ifBlank { null },
                    isPrimary = isPrimary,
                )
            }
        }
    }
}
