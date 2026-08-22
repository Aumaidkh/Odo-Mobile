package com.hopcape.odo.infrastructure.supabase.auth

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.auth.OtpRequestOutcome
import com.hopcape.odo.core.domain.auth.PhoneVerificationOutcome
import com.hopcape.odo.core.domain.auth.PhoneVerifier
import com.hopcape.odo.core.domain.auth.VerifiedPhoneToken
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.infrastructure.supabase.MockResponse
import com.hopcape.odo.infrastructure.supabase.SupabaseTestHarness
import com.hopcape.odo.infrastructure.supabase.bodyText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The two ways into a session, against a scripted server.
 *
 * The bridge gateway is the one that matters. Firebase proves the number and Supabase issues
 * the session, so a sign-in crosses two systems, and what the code screen shows depends on
 * which of them said no — retype it, ask for a new one, or wait.
 */
class SupabaseAuthGatewayTest {

    private val phone = PhoneNumber.of("9812345678").getOrNull()!!

    /* ---- asking for a code ---- */

    @Test
    fun askingForACodeGoesToTheVerifier_notToSupabase() = runTest {
        val harness = SupabaseTestHarness { MockResponse("{}") }
        val verifier = FakeVerifier()

        val result = bridge(harness, verifier).requestOtp(phone)

        assertTrue(result.isRight())
        assertEquals(phone, verifier.startedWith)
        // Supabase has no part in sending the code. A request here would mean the old
        // GoTrue OTP path had crept back in, which needs DLT registration and does not work.
        assertTrue(harness.requests.isEmpty())
    }

    @Test
    fun aVerifierThatCannotSendIsReportedAsIs() = runTest {
        val harness = SupabaseTestHarness { MockResponse("{}") }
        val verifier = FakeVerifier(startResult = DomainError.TooManyOtpRequests(30).left())

        assertEquals(
            DomainError.TooManyOtpRequests(30),
            bridge(harness, verifier).requestOtp(phone).leftOrNull(),
        )
    }

    /**
     * Firebase can prove some numbers by attestation alone, with no SMS ever sent. #188:
     * that used to leave the owner on a code screen waiting for a message that was never
     * coming, because the credential was fetched and then quietly discarded. It now trades
     * straight through to a session, the same as a typed code would.
     */
    @Test
    fun aNumberVerifiedWithoutACodeSignsInImmediately() = runTest {
        val harness = SupabaseTestHarness { MockResponse(tokenJson()) }
        val verifier = FakeVerifier(
            startResult = PhoneVerificationOutcome.AlreadyVerified.right(),
            autoVerificationResult = VerifiedPhoneToken("firebase-id-token").right(),
        )

        val outcome = bridge(harness, verifier).requestOtp(phone).getOrNull()

        val alreadyVerified = assertIs<OtpRequestOutcome.AlreadyVerified>(outcome)
        assertEquals("access-1", alreadyVerified.session.accessToken)
        assertEquals(OwnerId("user-1"), alreadyVerified.session.ownerId)
        assertTrue(verifier.completedAutoVerification)
        assertContains(harness.onlyRequest().bodyText(), "firebase-id-token")
    }

    @Test
    fun aFailedAutoVerificationExchangeIsReportedAsIs() = runTest {
        val harness = SupabaseTestHarness { MockResponse("{}") }
        val verifier = FakeVerifier(
            startResult = PhoneVerificationOutcome.AlreadyVerified.right(),
            autoVerificationResult = DomainError.OtpExpired.left(),
        )

        val error = bridge(harness, verifier).requestOtp(phone).leftOrNull()

        assertIs<DomainError.OtpExpired>(error)
        // Nothing was proved, so there is nothing to trade.
        assertTrue(harness.requests.isEmpty())
    }

    /* ---- trading a verified number for a session ---- */

    @Test
    fun aVerifiedNumberIsTradedForARealSession() = runTest {
        val harness = SupabaseTestHarness { MockResponse(tokenJson()) }
        val verifier = FakeVerifier(submitResult = VerifiedPhoneToken("firebase-id-token").right())

        val session = bridge(harness, verifier).verifyOtp(phone, "123456").getOrNull()

        assertEquals("access-1", session?.accessToken)
        // A Supabase user id, not the Firebase UID — this is the whole point of the exchange.
        assertEquals(OwnerId("user-1"), session?.ownerId)

        val request = harness.onlyRequest()
        assertEquals("/functions/v1/firebase-session", request.url.encodedPath)
        assertContains(request.bodyText(), "firebase-id-token")
        assertEquals("123456", verifier.submittedCode)
    }

    @Test
    fun aWrongCodeNeverReachesSupabase() = runTest {
        val harness = SupabaseTestHarness { MockResponse(tokenJson()) }
        val verifier = FakeVerifier(submitResult = DomainError.InvalidOtp.left())

        val error = bridge(harness, verifier).verifyOtp(phone, "000000").leftOrNull()

        assertIs<DomainError.InvalidOtp>(error)
        // Nothing was proved, so there is nothing to trade. Calling anyway would burn a
        // round trip on every mistyped digit.
        assertTrue(harness.requests.isEmpty())
    }

    /**
     * The ID token expired between the code screen and the exchange, or was minted for
     * another Firebase project. Either way the proof is gone and a retyped code cannot bring
     * it back — the owner needs a new one.
     */
    @Test
    fun aRefusedProofReadsAsExpired_soTheOwnerResends() = runTest {
        val harness = SupabaseTestHarness {
            MockResponse("""{"error_code":"invalid_token"}""", HttpStatusCode.Unauthorized)
        }
        val verifier = FakeVerifier(submitResult = VerifiedPhoneToken("stale").right())

        assertIs<DomainError.OtpExpired>(bridge(harness, verifier).verifyOtp(phone, "123456").leftOrNull())
    }

    @Test
    fun aFunctionThatFailsIsNotReportedAsAWrongCode() = runTest {
        val harness = SupabaseTestHarness {
            MockResponse("""{"error_code":"session_mint_failed"}""", HttpStatusCode.InternalServerError)
        }
        val verifier = FakeVerifier(submitResult = VerifiedPhoneToken("good").right())

        // Telling someone their code was wrong when the server broke sends them round a loop
        // that cannot end.
        assertIs<DomainError.OtpRequestFailed>(bridge(harness, verifier).verifyOtp(phone, "123456").leftOrNull())
    }

    /* ---- refresh + sign-out ---- */

    @Test
    fun refreshIsPureSupabase_theVerifierIsNotInvolved() = runTest {
        val harness = SupabaseTestHarness { MockResponse(tokenJson()) }
        val verifier = FakeVerifier()

        val session = bridge(harness, verifier).refresh("refresh-0").getOrNull()

        assertEquals("access-1", session?.accessToken)
        assertEquals("refresh_token", harness.onlyRequest().url.parameters["grant_type"])
        assertFalse(verifier.forgotten)
    }

    @Test
    fun aRejectedRefreshIsTerminal() = runTest {
        val harness = SupabaseTestHarness {
            MockResponse("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest)
        }

        // Not retryable: the token is revoked or past renewal, so the caller goes offline
        // rather than looping.
        assertIs<DomainError.SessionExpired>(bridge(harness, FakeVerifier()).refresh("revoked").leftOrNull())
    }

    @Test
    fun signOutRevokesTheSessionAndDropsTheFirebaseUser() = runTest {
        val harness = SupabaseTestHarness { MockResponse("{}", HttpStatusCode.NoContent) }
        val verifier = FakeVerifier()

        bridge(harness, verifier).signOut("access-1")

        assertEquals("/auth/v1/logout", harness.onlyRequest().url.encodedPath)
        assertEquals("Bearer access-1", harness.onlyRequest().headers["Authorization"])
        assertTrue(verifier.forgotten)
    }

    /**
     * Firebase keeps its own signed-in user, independently of Odo's session. Leaving one
     * behind because the server was unreachable means the next person to open the app on this
     * device is already verified as the last one.
     */
    @Test
    fun theFirebaseUserIsDroppedEvenWhenTheServerRefuses() = runTest {
        val harness = SupabaseTestHarness {
            MockResponse("{}", HttpStatusCode.InternalServerError)
        }
        val verifier = FakeVerifier()

        bridge(harness, verifier).signOut("access-1")

        assertTrue(verifier.forgotten)
    }

    /* ---- the development gateway ---- */

    @Test
    fun theDevGatewaySendsNoSmsAndSaysSo() = runTest {
        val harness = SupabaseTestHarness { MockResponse("{}") }

        val result = devGateway(harness).requestOtp(phone)

        assertTrue(result.isRight())
        // Nothing to send, so nothing is sent — but the flow still reaches the code screen.
        assertTrue(harness.requests.isEmpty())
    }

    @Test
    fun theDevGatewaySignsInAsTheFixedAccountWhateverIsTyped() = runTest {
        val harness = SupabaseTestHarness { MockResponse(tokenJson()) }

        val session = devGateway(harness).verifyOtp(phone, "any-code-at-all").getOrNull()

        assertEquals("access-1", session?.accessToken)
        val request = harness.onlyRequest()
        assertEquals("password", request.url.parameters["grant_type"])
        assertContains(request.bodyText(), DevCredentials.EMAIL)
    }

    /* ---- scaffolding ---- */

    private fun bridge(harness: SupabaseTestHarness, verifier: PhoneVerifier) =
        FirebaseBridgeAuthGateway(verifier = verifier, endpoint = endpoint(harness))

    private fun devGateway(harness: SupabaseTestHarness) = DevPasswordAuthGateway(endpoint(harness))

    private fun endpoint(harness: SupabaseTestHarness) = SupabaseTokenEndpoint(
        client = harness.client,
        environment = harness.environment,
        telemetry = harness.telemetry,
    )

    private fun tokenJson() = """
        {"access_token":"access-1","refresh_token":"refresh-1","expires_in":3600,
         "user":{"id":"user-1"}}
    """.trimIndent()

    private class FakeVerifier(
        private val startResult: Either<DomainError, PhoneVerificationOutcome> =
            PhoneVerificationOutcome.CodeSent.right(),
        private val submitResult: Either<DomainError, VerifiedPhoneToken> =
            VerifiedPhoneToken("token").right(),
        private val autoVerificationResult: Either<DomainError, VerifiedPhoneToken> = submitResult,
    ) : PhoneVerifier {

        var startedWith: PhoneNumber? = null
            private set
        var submittedCode: String? = null
            private set
        var completedAutoVerification: Boolean = false
            private set
        var forgotten: Boolean = false
            private set

        override suspend fun startVerification(phone: PhoneNumber): Either<DomainError, PhoneVerificationOutcome> {
            startedWith = phone
            return startResult
        }

        override suspend fun submitCode(code: String): Either<DomainError, VerifiedPhoneToken> {
            submittedCode = code
            return submitResult
        }

        override suspend fun completeAutoVerification(): Either<DomainError, VerifiedPhoneToken> {
            completedAutoVerification = true
            return autoVerificationResult
        }

        override suspend fun forget() {
            forgotten = true
        }
    }
}
