package com.hopcape.odo.core.domain.car.model

import arrow.core.EitherNel
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import arrow.core.raise.zipOrAccumulate
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DomainError

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
    val odometer: Distance,
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
                { Distance.of(odometerKm).bind() },
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

        /**
         * Rehydrate a [Car] from already-persisted, trusted data (the local DB,
         * which mirrors these invariants via CHECK constraints).
         *
         * Unlike [create], this does **not** accumulate validation errors: the
         * row was written by a previously-valid [Car], so a value that fails to
         * reconstruct signals local data corruption — a programming/storage
         * error, not user input — and fails fast. Construction stays inside the
         * domain, so the value objects' private constructors remain private.
         */
        fun reconstitute(
            id: CarId,
            ownerId: OwnerId,
            make: String,
            model: String,
            variant: String?,
            year: Int,
            fuelType: FuelType,
            registrationNumber: String?,
            odometerKm: Int,
            purchaseYear: Int?,
            nickname: String?,
            isPrimary: Boolean,
        ): Car = Car(
            id = id,
            ownerId = ownerId,
            make = make,
            model = model,
            variant = variant,
            year = ModelYear.of(year).getOrElse { error("corrupt car.year=$year for ${id.value}") },
            fuelType = fuelType,
            registrationNumber = RegistrationNumber.of(registrationNumber),
            odometer = Distance.of(odometerKm)
                .getOrElse { error("corrupt car.odometer=$odometerKm for ${id.value}") },
            purchaseYear = PurchaseYear.of(purchaseYear)
                .getOrElse { error("corrupt car.purchaseYear=$purchaseYear for ${id.value}") },
            nickname = nickname,
            isPrimary = isPrimary,
        )
    }
}
