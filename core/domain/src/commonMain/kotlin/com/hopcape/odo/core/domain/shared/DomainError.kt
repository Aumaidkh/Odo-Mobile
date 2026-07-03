package com.hopcape.odo.core.domain.shared

/**
 * The domain's failure vocabulary — part of the ubiquitous language.
 *
 * Lives in `:core:domain` (not `:core:common`) because every variant encodes
 * domain knowledge (odometer, fuel, year…). Inner/data layers depend on domain,
 * so they can map their own failures into [PersistenceFailure].
 */
sealed interface DomainError {

    /** Odometer is mandatory on every car (Odo's core number) but was absent. */
    data object MissingOdometer : DomainError

    /** Odometer was provided but negative. */
    data object NegativeOdometer : DomainError

    /** Manufacturing year is mandatory but was absent. */
    data object MissingYear : DomainError

    /** A year value fell outside the accepted 1980..2100 range. */
    data class YearOutOfRange(val field: String, val value: Int) : DomainError

    /** Fuel type is mandatory but was absent. */
    data object MissingFuelType : DomainError

    /** Make is mandatory and must be non-blank. */
    data object BlankMake : DomainError

    /** Model is mandatory and must be non-blank. */
    data object BlankModel : DomainError

    /** Service date is mandatory on every log but was absent. */
    data object MissingServiceDate : DomainError

    /** Service date was provided but lies in the future. */
    data object ServiceDateInFuture : DomainError

    /** A money amount was provided but negative (money is unsigned paise). */
    data object NegativeAmount : DomainError

    /** Workshop name exceeded [max] characters after trimming. */
    data class WorkshopNameTooLong(val max: Int) : DomainError

    /** Notes exceeded [max] characters after trimming. */
    data class NotesTooLong(val max: Int) : DomainError

    /**
     * A new/edited odometer reading moved backwards past the car's last known
     * reading — Odo enforces the odometer as an ever-increasing number.
     */
    data class OdometerRegression(val previousKm: Int, val attemptedKm: Int) : DomainError

    /** The referenced car has no baseline reading — it does not exist for the owner. */
    data object CarNotFound : DomainError

    /** A persistence/infrastructure failure mapped up from an outer layer. */
    data class PersistenceFailure(val cause: String? = null) : DomainError
}
