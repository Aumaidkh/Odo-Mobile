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

    /** The owner's contact email was present but not a usable address. */
    data object InvalidOwnerEmail : DomainError

    /** The owner's contact email exceeded [max] characters after trimming. */
    data class OwnerEmailTooLong(val max: Int) : DomainError

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

    /**
     * No profile is stored on this device. Onboarding writes one before the app opens, so
     * this means it was deleted — an edit has nothing to edit.
     */
    data object ProfileNotFound : DomainError

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

    /* ---- Scanning ---- */

    /**
     * The photo yielded nothing usable — too blurry, too dark, or not a bill at all. An
     * ordinary outcome of pointing a camera at a crumpled receipt, so the screen offers a
     * retake or manual entry rather than treating it as a fault.
     */
    data object ScanUnreadable : DomainError

    /**
     * The owner's plan has no AI scans left this period ([limit] included). Not a validation
     * failure — the photo was fine — so the surface that catches this offers Pro.
     *
     * The client's copy of the count is a mirror; the Edge Function enforces the real one.
     */
    data class ScanQuotaExhausted(val limit: Int) : DomainError

    /** The scan came back, but with nothing in it worth showing the owner. */
    data object ScanRejected : DomainError

    /**
     * There is no extractor to ask — the AI service could not be reached, or this build has
     * none configured.
     *
     * Kept apart from [ScanUnreadable] because the two lead somewhere different: an
     * unreadable photo invites a retake, while an unavailable service means retaking is
     * pointless and the honest offer is manual entry.
     */
    data object ScanUnavailable : DomainError

    /* ---- Payments ---- */

    /** The owner backed out of the store's purchase sheet without paying. */
    data object PaymentCancelled : DomainError

    /**
     * The card was declined, or the store reported a failure. Distinct from
     * [PaymentCancelled] because one is the owner's choice and the other is not.
     */
    data object PaymentFailed : DomainError


    /* ---- Subscriptions ---- */

    /**
     * The store could not be reached, so what Pro costs is not known right now.
     *
     * Worth retrying: the network, the Play Store app or RevenueCat is momentarily
     * unavailable, and the same request a moment later usually answers. The paywall shows a
     * retry rather than a price, because a price it cannot confirm is worse than none.
     */
    data object StoreUnavailable : DomainError

    /**
     * The store answered and has nothing to sell.
     *
     * Distinct from [StoreUnavailable] because retrying will not help: either no offering is
     * marked current in the RevenueCat dashboard, or its packages are not the monthly and
     * annual plans this app knows how to show. It is a configuration mistake, not a network
     * one, and it is the app's own fault rather than the owner's.
     */
    data object NothingForSale : DomainError

    /* ---- Fuel fills ---- */

    /** A fill was submitted without the day it happened. */
    data object MissingFillDate : DomainError

    /** A fill claimed a date in the future. */
    data object FillDateInFuture : DomainError

    /**
     * A fill was submitted with no fuel in it. Zero is rejected rather than stored as
     * "unknown": it makes the measured mileage meaningless with no way to tell afterwards.
     */
    data object MissingFuelQuantity : DomainError

    /* ---- Reminders ---- */

    /** A custom reminder was submitted without a topic name — there is nothing to say. */
    data object BlankReminderTitle : DomainError

    /** A reminder title exceeded [max] characters after trimming. */
    data class ReminderTitleTooLong(val max: Int) : DomainError

    /** A repeating cadence was given a step of zero or less (days or kilometres). */
    data object ReminderIntervalNotPositive : DomainError

    /**
     * A reminder's first nudge was set before today. It would be late the moment it was
     * saved, so the field is corrected rather than the date stored.
     */
    data object ReminderStartInPast : DomainError

    /** A distance-based reminder needs the odometer reading it counts from, but none was given. */
    data object MissingReminderAnchorOdometer : DomainError

    /** No live custom reminder has this id — it was never written, or has been deleted. */
    data object ReminderNotFound : DomainError

    /* ---- Auth ---- */

    /** A phone number was required to sign in but nothing was typed. */
    data object BlankPhoneNumber : DomainError

    /**
     * Something was typed, but it is not a number an SMS could reach — the wrong length, or
     * characters that are not digits once separators are removed.
     */
    data object InvalidPhoneNumber : DomainError

    /** The code was wrong. The owner retypes it; the same code may still be live. */
    data object InvalidOtp : DomainError

    /**
     * The code was right but too old. Distinct from [InvalidOtp] because the answer is
     * different: resend, don't retype.
     */
    data object OtpExpired : DomainError

    /**
     * Too many codes asked for, too fast — by our own limit or the server's. [retryAfter]
     * is how long until another is allowed, so the screen can count down instead of
     * guessing.
     */
    data class TooManyOtpRequests(val retryAfterSeconds: Long) : DomainError

    /** The code could not be sent at all: no network, or the SMS provider refused. */
    data object OtpRequestFailed : DomainError

    /**
     * The session is gone and could not be renewed — revoked, or the refresh token expired.
     * The app keeps working offline; only syncing stops until someone signs in again.
     *
     * **Terminal.** Whoever holds the session is expected to throw it away on seeing this.
     * That is why [SessionUnavailable] exists beside it: everything used to arrive here,
     * including a renewal that simply never got an answer.
     */
    data object SessionExpired : DomainError

    /**
     * The session could not be renewed *right now* — a timeout, a dropped connection, a 5xx
     * from the identity service. Worth retrying, and emphatically not a reason to sign
     * anybody out.
     *
     * Distinct from [SessionExpired] because the two used to be the same value, so a train
     * tunnel could end a session as decisively as a revoked token. The install stopped
     * syncing with nothing on screen to say so, and the next sign-in pulled a delta from
     * cursors the owner believed they had cleared (issue #312).
     */
    data object SessionUnavailable : DomainError

    /* ---- Account deletion ---- */

    /**
     * There is no provider account behind this session, so nothing can be re-verified and
     * nothing can be deleted server-side. Reached on a device that only ever held a local
     * profile, where the honest outcome is the local wipe on its own.
     */
    data object NoVerifiedAccount : DomainError

    /**
     * The number has to be proved again before the account can go. Not a failure of the
     * owner's — the erase endpoint refuses any proof older than ten minutes, on purpose,
     * because an hour-old token is not enough to authorise something irreversible.
     */
    data object ReVerificationRequired : DomainError

    /**
     * The server refused or could not complete the erase. [code] is its own reason string
     * when it gave one, kept as-is: this ends up in a support conversation about an account
     * that is still there, and a paraphrase would lose the only clue.
     */
    data class AccountEraseFailed(val code: String? = null) : DomainError

    /**
     * The account is gone from the server but this device still holds a copy — the local
     * wipe failed after the point of no return.
     *
     * Its own case because it is the one outcome that must not be reported as either success
     * or plain failure: nothing can be recovered by retrying the erase, and the only useful
     * offer is to retry the wipe.
     */
    data object LocalDataSurvivedErase : DomainError

    /* ---- Challans ---- */

    /**
     * The challan records source did not answer — down, unreachable, or refusing.
     *
     * Its own case (not [PersistenceFailure]) because the screen's response is specific:
     * say whose problem it is, show the last known result with its age, and offer a
     * retry — none of which fits a generic storage failure.
     */
    data object ChallanRecordsUnreachable : DomainError

    /* ---- Trips ---- */

    /** No live trip has this id — it was never written, or has been deleted. */
    data object TripNotFound : DomainError

    /** A trip's end time was not after its start time. */
    data object InvalidTripWindow : DomainError

    /** A latitude/longitude pair fell outside the valid -90..90 / -180..180 range. */
    data class InvalidCoordinate(val field: String, val value: Double) : DomainError

    /** A persistence/infrastructure failure mapped up from an outer layer. */
    data class PersistenceFailure(val cause: String? = null) : DomainError
}
