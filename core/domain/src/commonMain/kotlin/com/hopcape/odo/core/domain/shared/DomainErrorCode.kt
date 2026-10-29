package com.hopcape.odo.core.domain.shared

import com.hopcape.odo.core.common.error.ErrorCode
import com.hopcape.odo.core.common.error.errorCode

/**
 * The stable analytics code for this error, written out per kind so a class rename never
 * renames a dashboard value. Changing a code is a deliberate break in history.
 */
fun DomainError.code(): String = when (this) {
    DomainError.MissingOdometer -> "missing_odometer"
    DomainError.NegativeOdometer -> "negative_odometer"
    DomainError.MissingYear -> "missing_year"
    is DomainError.YearOutOfRange -> "year_out_of_range"
    DomainError.MissingFuelType -> "missing_fuel_type"
    DomainError.BlankMake -> "blank_make"
    DomainError.BlankModel -> "blank_model"
    DomainError.BlankOwnerName -> "blank_owner_name"
    is DomainError.OwnerNameTooShort -> "owner_name_too_short"
    is DomainError.OwnerNameTooLong -> "owner_name_too_long"
    DomainError.InvalidOwnerEmail -> "invalid_owner_email"
    is DomainError.OwnerEmailTooLong -> "owner_email_too_long"
    DomainError.MissingOnboardingGoal -> "missing_onboarding_goal"
    DomainError.BlankRegistrationNumber -> "blank_registration_number"
    DomainError.RegistrationNotFound -> "registration_not_found"
    DomainError.LookupOffline -> "lookup_offline"
    DomainError.LookupUnavailable -> "lookup_unavailable"
    DomainError.MissingServiceDate -> "missing_service_date"
    DomainError.ServiceDateInFuture -> "service_date_in_future"
    DomainError.NegativeAmount -> "negative_amount"
    is DomainError.WorkshopNameTooLong -> "workshop_name_too_long"
    is DomainError.NotesTooLong -> "notes_too_long"
    is DomainError.OdometerRegression -> "odometer_regression"
    is DomainError.OdometerAheadOfLaterEntry -> "odometer_ahead_of_later_entry"
    DomainError.CarNotFound -> "car_not_found"
    DomainError.ProfileNotFound -> "profile_not_found"
    DomainError.ServiceLogNotFound -> "service_log_not_found"
    DomainError.EmptyTicketBody -> "empty_ticket_body"
    is DomainError.TicketBodyTooLong -> "ticket_body_too_long"
    DomainError.MissingDocumentFile -> "missing_document_file"
    is DomainError.DocumentTitleTooLong -> "document_title_too_long"
    DomainError.IssueDateInFuture -> "issue_date_in_future"
    DomainError.ExpiryBeforeIssueDate -> "expiry_before_issue_date"
    DomainError.DocumentNotFound -> "document_not_found"
    is DomainError.DocumentLimitReached -> "document_limit_reached"
    is DomainError.FuelPriceOutOfRange -> "fuel_price_out_of_range"
    DomainError.ScanUnreadable -> "scan_unreadable"
    is DomainError.ScanQuotaExhausted -> "scan_quota_exhausted"
    DomainError.ScanRejected -> "scan_rejected"
    DomainError.ScanUnavailable -> "scan_unavailable"
    DomainError.PaymentCancelled -> "payment_cancelled"
    DomainError.PaymentFailed -> "payment_failed"
    DomainError.StoreUnavailable -> "store_unavailable"
    DomainError.NothingForSale -> "nothing_for_sale"
    DomainError.MissingFillDate -> "missing_fill_date"
    DomainError.FillDateInFuture -> "fill_date_in_future"
    DomainError.MissingFuelQuantity -> "missing_fuel_quantity"
    DomainError.BlankReminderTitle -> "blank_reminder_title"
    is DomainError.ReminderTitleTooLong -> "reminder_title_too_long"
    DomainError.ReminderIntervalNotPositive -> "reminder_interval_not_positive"
    DomainError.ReminderStartInPast -> "reminder_start_in_past"
    DomainError.MissingReminderAnchorOdometer -> "missing_reminder_anchor_odometer"
    DomainError.ReminderNotFound -> "reminder_not_found"
    DomainError.BlankPhoneNumber -> "blank_phone_number"
    DomainError.InvalidPhoneNumber -> "invalid_phone_number"
    DomainError.InvalidOtp -> "invalid_otp"
    DomainError.OtpExpired -> "otp_expired"
    is DomainError.TooManyOtpRequests -> "too_many_otp_requests"
    DomainError.OtpRequestFailed -> "otp_request_failed"
    DomainError.SessionExpired -> "session_expired"
    DomainError.SessionUnavailable -> "session_unavailable"
    DomainError.NoVerifiedAccount -> "no_verified_account"
    DomainError.ReVerificationRequired -> "re_verification_required"
    is DomainError.AccountEraseFailed -> "account_erase_failed"
    DomainError.LocalDataSurvivedErase -> "local_data_survived_erase"
    DomainError.ChallanRecordsUnreachable -> "challan_records_unreachable"
    DomainError.TripNotFound -> "trip_not_found"
    DomainError.InvalidTripWindow -> "invalid_trip_window"
    is DomainError.InvalidCoordinate -> "invalid_coordinate"
    is DomainError.PersistenceFailure -> "persistence_failure"
}

/**
 * The analytics code for any failure a telemetry facade is handed: a [DomainError], a
 * [Throwable], or anything else (`unexpected`). Lives here because `:core:common` cannot see
 * [DomainError].
 */
fun failureCode(failure: Any?): String = when (failure) {
    is DomainError -> failure.code()
    is Throwable -> errorCode(failure)
    else -> ErrorCode.UNEXPECTED
}
