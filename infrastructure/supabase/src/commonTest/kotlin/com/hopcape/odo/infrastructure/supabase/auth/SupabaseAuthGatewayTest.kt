package com.hopcape.odo.infrastructure.supabase.auth

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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The two ways into a session, against a scripted GoTrue.
 *
 * The error mapping is the part worth pinning. Three failures look alike over HTTP and mean
 * completely different things to whoever is staring at the code screen: retype it, ask for a
 * new one, or wait.
 */
class SupabaseAuthGatewayTest {

    private val phone = PhoneNumber.of("9812345678").getOrNull()!!

    /* ---- phone OTP ---- */

    @Test
    fun requestingACodePostsTheNumberInE164() = runTest {
        val harness = SupabaseTestHarness { MockResponse("{}") }
        otpGateway(harness).requestOtp(phone)

        val request = harness.onlyRequest()
        assertEquals("/auth/v1/otp", request.url.encodedPath)
        assertContains(request.bodyText(), "+919812345678")
    }

    @Test
    fun verifyingACodeYieldsASession() = runTest {
        val harness = SupabaseTestHarness { MockResponse(tokenJson()) }

        val session = otpGateway(harness).verifyOtp(phone, "123456").getOrNull()

        assertEquals("access-1", session?.accessToken)
        assertEquals(OwnerId("user-1"), session?.ownerId)
        assertEquals("/auth/v1/verify", harness.onlyRequest().url.encodedPath)
        // GoTrue needs to be told which kind of code this is.
        assertContains(harness.onlyRequest().bodyText(), "\"sms\"")
    }

    @Test
    fun aWrongCodeAndAnExpiredCodeAreDifferentAnswers() = runTest {
        // Retype vs resend — the screen cannot offer the right one if these collapse.
        val wrong = SupabaseTestHarness {
            MockResponse("""{"error_code":"invalid_grant","msg":"Token has expired or is invalid"}""", HttpStatusCode.Forbidden)
        }
        assertIs<DomainError.InvalidOtp>(otpGateway(wrong).verifyOtp(phone, "000000").leftOrNull())

        val expired = SupabaseTestHarness {
            MockResponse("""{"error_code":"otp_expired","msg":"Token has expired"}""", HttpStatusCode.Forbidden)
        }
        assertIs<DomainError.OtpExpired>(otpGateway(expired).verifyOtp(phone, "123456").leftOrNull())
    }

    @Test
    fun rateLimitingCarriesHowLongToWait() = runTest {
        val harness = SupabaseTestHarness {
            MockResponse("""{"msg":"too many requests"}""", HttpStatusCode.TooManyRequests, headers = mapOf("Retry-After" to "45"))
        }

        val error = otpGateway(harness).requestOtp(phone).leftOrNull()

        // The screen counts down instead of guessing.
        assertEquals(DomainError.TooManyOtpRequests(45), error)
    }

    @Test
    fun aRateLimitWithNoHeaderStillGivesTheScreenANumber() = runTest {
        val harness = SupabaseTestHarness {
            MockResponse("""{"msg":"too many requests"}""", HttpStatusCode.TooManyRequests)
        }

        assertIs<DomainError.TooManyOtpRequests>(otpGateway(harness).requestOtp(phone).leftOrNull())
    }

    @Test
    fun anUnsentCodeIsItsOwnFailure() = runTest {
        // No SMS provider configured is what this looks like until DLT registration clears.
        val harness = SupabaseTestHarness {
            MockResponse("""{"msg":"Error sending sms"}""", HttpStatusCode.InternalServerError)
        }

        assertIs<DomainError.OtpRequestFailed>(otpGateway(harness).requestOtp(phone).leftOrNull())
    }

    /* ---- refresh + sign-out ---- */

    @Test
    fun aRejectedRefreshIsTerminal() = runTest {
        val harness = SupabaseTestHarness {
            MockResponse("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest)
        }

        // Not retryable: the token is revoked or past renewal, so the caller goes offline
        // rather than looping.
        assertIs<DomainError.SessionExpired>(otpGateway(harness).refresh("revoked").leftOrNull())
    }

    @Test
    fun signOutSendsTheTokenItIsRevoking() = runTest {
        val harness = SupabaseTestHarness { MockResponse("{}", HttpStatusCode.NoContent) }

        otpGateway(harness).signOut("access-1")

        assertEquals("/auth/v1/logout", harness.onlyRequest().url.encodedPath)
        assertEquals("Bearer access-1", harness.onlyRequest().headers["Authorization"])
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

    private fun otpGateway(harness: SupabaseTestHarness) = SupabaseOtpAuthGateway(endpoint(harness))

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
}
