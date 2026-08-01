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

    /** The owner's name is mandatory on a profile but was absent or blank. */
    data object BlankOwnerName : DomainError

    /** The owner's name was shorter than [min] characters after trimming. */
    data class OwnerNameTooShort(val min: Int) : DomainError

    /** The owner's name exceeded [max] characters after trimming. */
    data class OwnerNameTooLong(val max: Int) : DomainError

    /**
     * An onboarding goal was required but not chosen. It decides the surface the owner
     * lands on (PRD §5.1), so setup can't finish without one.
     */
    data object MissingOnboardingGoal : DomainError

    /** A registration number was required (e.g. to look a car up) but was absent or blank. */
    data object BlankRegistrationNumber : DomainError

    /**
     * The registry answered and has no vehicle for this plate. Permanent — retrying will
     * not help, so the owner is sent to manual entry.
     */
    data object RegistrationNotFound : DomainError

    /** A lookup couldn't be attempted because the device is offline. Worth retrying. */
    data object LookupOffline : DomainError

    /** The lookup service failed or timed out. Worth retrying. */
    data object LookupUnavailable : DomainError

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
     * A new/edited odometer reading fell below the nearest reading *before* its service
     * date — Odo enforces the odometer as an ever-increasing number.
     */
    data class OdometerRegression(val previousKm: Int, val attemptedKm: Int) : DomainError

    /**
     * A new/edited odometer reading rose above the nearest reading *after* its service
     * date. The mirror of [OdometerRegression]: backdating a service at 60,000 km when the
     * car already read 55,000 km a month later is the same impossibility seen from the
     * other side.
     */
    data class OdometerAheadOfLaterEntry(val nextKm: Int, val attemptedKm: Int) : DomainError

    /** The referenced car has no baseline reading — it does not exist for the owner. */
    data object CarNotFound : DomainError

    /** No live service log has this id — it was never written, or has been deleted. */
    data object ServiceLogNotFound : DomainError

    /**
     * A document was submitted without a stored file. Every vault entry keeps the paper
     * itself (DB `documents.storage_path` is `NOT NULL`) — an entry with only dates is a
     * reminder, not a document.
     */
    data object MissingDocumentFile : DomainError

    /** A document title exceeded [max] characters after trimming. */
    data class DocumentTitleTooLong(val max: Int) : DomainError

    /** A document claimed an issue date in the future — nothing has been issued yet. */
    data object IssueDateInFuture : DomainError

    /** A document's expiry fell before its issue date; a paper cannot lapse before it exists. */
    data object ExpiryBeforeIssueDate : DomainError

    /** No live document has this id — it was never written, or has been deleted. */
    data object DocumentNotFound : DomainError

    /**
     * The owner's plan already holds as many documents as it permits ([limit]). Not a
     * validation failure — the document was fine — so the surface that catches this offers
     * an upgrade, never a corrected field.
     */
    data class DocumentLimitReached(val limit: Int) : DomainError

    /**
     * A fuel price the owner typed fell outside what a pump can plausibly charge — below
     * [minPaise] or above [maxPaise] per unit. Almost always a slipped decimal point, so
     * the field is corrected rather than the number stored.
     */
    data class FuelPriceOutOfRange(val minPaise: Long, val maxPaise: Long) : DomainError

    /** A persistence/infrastructure failure mapped up from an outer layer. */
    data class PersistenceFailure(val cause: String? = null) : DomainError
}
