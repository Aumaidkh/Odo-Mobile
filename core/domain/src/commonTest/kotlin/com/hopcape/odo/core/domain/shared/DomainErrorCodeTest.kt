package com.hopcape.odo.core.domain.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DomainErrorCodeTest {

    private val everyKind: List<DomainError> = listOf(
        DomainError.MissingOdometer,
        DomainError.NegativeOdometer,
        DomainError.MissingYear,
        DomainError.YearOutOfRange("x", 1),
        DomainError.MissingFuelType,
        DomainError.BlankMake,
        DomainError.BlankModel,
        DomainError.BlankOwnerName,
        DomainError.OwnerNameTooShort(1),
        DomainError.OwnerNameTooLong(1),
        DomainError.InvalidOwnerEmail,
        DomainError.OwnerEmailTooLong(1),
        DomainError.MissingOnboardingGoal,
        DomainError.BlankRegistrationNumber,
        DomainError.RegistrationNotFound,
        DomainError.LookupOffline,
        DomainError.LookupUnavailable,
        DomainError.MissingServiceDate,
        DomainError.ServiceDateInFuture,
        DomainError.NegativeAmount,
        DomainError.WorkshopNameTooLong(1),
        DomainError.NotesTooLong(1),
        DomainError.OdometerRegression(1, 1),
        DomainError.OdometerAheadOfLaterEntry(1, 1),
        DomainError.CarNotFound,
        DomainError.ProfileNotFound,
        DomainError.ServiceLogNotFound,
        DomainError.EmptyTicketBody,
        DomainError.TicketBodyTooLong(1),
        DomainError.MissingDocumentFile,
        DomainError.DocumentTitleTooLong(1),
        DomainError.IssueDateInFuture,
        DomainError.ExpiryBeforeIssueDate,
        DomainError.DocumentNotFound,
        DomainError.DocumentLimitReached(1),
        DomainError.FuelPriceOutOfRange(1L, 1L),
        DomainError.ScanUnreadable,
        DomainError.ScanQuotaExhausted(1),
        DomainError.ScanRejected,
        DomainError.ScanUnavailable,
        DomainError.PaymentCancelled,
        DomainError.PaymentFailed,
        DomainError.StoreUnavailable,
        DomainError.NothingForSale,
        DomainError.MissingFillDate,
        DomainError.FillDateInFuture,
        DomainError.MissingFuelQuantity,
        DomainError.BlankReminderTitle,
        DomainError.ReminderTitleTooLong(1),
        DomainError.ReminderIntervalNotPositive,
        DomainError.ReminderStartInPast,
        DomainError.MissingReminderAnchorOdometer,
        DomainError.ReminderNotFound,
        DomainError.BlankPhoneNumber,
        DomainError.InvalidPhoneNumber,
        DomainError.InvalidOtp,
        DomainError.OtpExpired,
        DomainError.TooManyOtpRequests(1L),
        DomainError.OtpRequestFailed,
        DomainError.SessionExpired,
        DomainError.SessionUnavailable,
        DomainError.NoVerifiedAccount,
        DomainError.ReVerificationRequired,
        DomainError.AccountEraseFailed(null),
        DomainError.LocalDataSurvivedErase,
        DomainError.ChallanRecordsUnreachable,
        DomainError.TripNotFound,
        DomainError.InvalidTripWindow,
        DomainError.InvalidCoordinate("x", 1.0),
        DomainError.PersistenceFailure(null),
    )

    @Test
    fun everyCodeIsSnakeCase() {
        val pattern = Regex("^[a-z][a-z0-9_]*$")
        everyKind.forEach { error ->
            val code = error.code()
            assertTrue(code.length >= 3 && pattern.matches(code), "bad code '$code' for $error")
        }
    }

    @Test
    fun noTwoKindsShareACode() {
        val duplicates = everyKind.groupBy { it.code() }.filterValues { it.size > 1 }
        assertEquals(emptyMap(), duplicates.mapValues { (_, kinds) -> kinds.map { it.toString() } })
    }

    @Test
    fun everyKindIsListed() {
        assertEquals(70, everyKind.map { it::class }.distinct().size)
    }

    @Test
    fun aDomainErrorFailsWithItsCode() = assertEquals("invalid_otp", failureCode(DomainError.InvalidOtp))

    @Test
    fun anUnknownExceptionFailsAsUnexpected() = assertEquals("unexpected", failureCode(IllegalStateException()))

    @Test
    fun anythingElseFailsAsUnexpected() = assertEquals("unexpected", failureCode("text"))
}
