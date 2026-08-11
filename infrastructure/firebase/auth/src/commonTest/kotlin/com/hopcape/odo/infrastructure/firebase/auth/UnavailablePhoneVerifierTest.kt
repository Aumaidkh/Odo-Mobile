package com.hopcape.odo.infrastructure.firebase.auth

import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnavailablePhoneVerifierTest {

    @Test
    fun `reports that no code could be sent, rather than a silent success`() = runTest {
        val verifier = UnavailablePhoneVerifier(onDiagnostic = {})

        val result = verifier.startVerification(phone())

        assertEquals(DomainError.OtpRequestFailed.left(), result)
    }

    /**
     * A target with no SMS must not look like a working one. Reporting success here would
     * put the owner on a code screen waiting for a message that is never coming.
     */
    @Test
    fun `says why, so a missing platform implementation is not invisible`() = runTest {
        val diagnostics = mutableListOf<String>()

        UnavailablePhoneVerifier(onDiagnostic = diagnostics::add).startVerification(phone())

        assertEquals(1, diagnostics.size)
        assertTrue(diagnostics.single().isNotBlank())
    }

    @Test
    fun `a code typed against no verification reads as expired, not wrong`() = runTest {
        val verifier = UnavailablePhoneVerifier(onDiagnostic = {})

        val result = verifier.submitCode("123456")

        assertEquals(DomainError.OtpExpired.left(), result)
    }

    private fun phone(): PhoneNumber = PhoneNumber.of("9876543210").getOrNull()!!
}

private fun DomainError.left() = arrow.core.Either.Left(this)
