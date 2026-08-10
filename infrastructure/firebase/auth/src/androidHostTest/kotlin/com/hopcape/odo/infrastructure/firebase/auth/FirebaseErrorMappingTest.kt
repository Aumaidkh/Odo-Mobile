package com.hopcape.odo.infrastructure.firebase.auth

import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.hopcape.odo.core.domain.shared.DomainError
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The mapping is where every decision in this adapter lives. Which `DomainError` comes back
 * is what the sign-in screens read to choose between "retype", "resend" and "go back and fix
 * the number", so getting it wrong sends the owner down a path that cannot work.
 *
 * The SDK plumbing around it — the callback object, the Activity, the token exchange — is not
 * unit-testable: `FirebaseAuth` is final and this repo has no mocking library. That path is
 * covered by the instrumented test against a Firebase test number.
 */
class FirebaseErrorMappingTest {

    /* ---- asking for a code ---- */

    @Test
    fun `a bad number is not a send failure, because resending cannot fix it`() {
        val error = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_PHONE_NUMBER", "bad number")

        assertEquals(DomainError.InvalidPhoneNumber, error.toSendFailure())
    }

    @Test
    fun `a rate limit carries a countdown, so the screen does not have to guess`() {
        val error = FirebaseTooManyRequestsException("slow down")

        assertEquals(DomainError.TooManyOtpRequests(retryAfterSeconds = 30L), error.toSendFailure())
    }

    @Test
    fun `anything else is a send failure`() {
        assertEquals(DomainError.OtpRequestFailed, IOException("offline").toSendFailure())
    }

    /* ---- submitting a code ---- */

    @Test
    fun `a wrong code reads as invalid, so the owner retypes`() {
        val error = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_VERIFICATION_CODE", "nope")

        assertEquals(DomainError.InvalidOtp, error.toVerifyFailure())
    }

    /**
     * The distinction that matters most. Firebase reports both through the same exception
     * family, and answering "wrong code" to an expired session leaves the owner retyping a
     * code that can never be accepted again.
     */
    @Test
    fun `an expired session reads as expired, so the owner resends instead`() {
        val error = FirebaseAuthException("ERROR_SESSION_EXPIRED", "too late")

        assertEquals(DomainError.OtpExpired, error.toVerifyFailure())
    }

    @Test
    fun `a rate limit on verify carries the same countdown as on send`() {
        val error = FirebaseTooManyRequestsException("slow down")

        assertEquals(DomainError.TooManyOtpRequests(retryAfterSeconds = 30L), error.toVerifyFailure())
    }

    @Test
    fun `anything else is not reported as a wrong code`() {
        assertEquals(DomainError.OtpRequestFailed, IOException("offline").toVerifyFailure())
    }
}
