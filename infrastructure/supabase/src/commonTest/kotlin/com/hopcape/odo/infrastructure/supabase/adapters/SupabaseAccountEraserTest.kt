package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.domain.auth.EraseOutcome
import com.hopcape.odo.core.domain.auth.VerifiedPhoneToken
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.infrastructure.supabase.MockResponse
import com.hopcape.odo.infrastructure.supabase.SupabaseTestHarness
import com.hopcape.odo.infrastructure.supabase.bodyText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The account-erase call, against a scripted server.
 *
 * The outcome mapping is the whole point: this adapter is the only thing that decides whether
 * an owner is told their account is gone, and a 200 it misreads would say so when it is not.
 */
class SupabaseAccountEraserTest {

    private val token = VerifiedPhoneToken("firebase-id-token")

    private fun harness(response: MockResponse) = SupabaseTestHarness { response }

    private fun eraser(harness: SupabaseTestHarness) = SupabaseAccountEraser(
        client = harness.client,
        environment = harness.environment,
        telemetry = harness.telemetry,
    )

    @Test
    fun posts_theIdTokenToTheLegalFunction() = runTest {
        val harness = harness(MockResponse("""{"status":"deleted"}"""))

        eraser(harness).erase(token)

        val request = harness.onlyRequest()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(
            "https://project.supabase.co/functions/v1/legal/delete-account",
            request.url.toString(),
        )
        assertTrue(request.bodyText().contains("firebase-id-token"))
    }

    @Test
    fun deleted_isSuccess() = runTest {
        val result = eraser(harness(MockResponse("""{"status":"deleted"}"""))).erase(token)

        assertEquals(EraseOutcome.DELETED, result.getOrNull())
    }

    @Test
    fun noAccount_isSuccessNotAFailure() = runTest {
        // The number was proved and the server has nothing under it. The flow carries on to
        // the local wipe rather than stopping to report a problem the owner cannot act on.
        val result = eraser(harness(MockResponse("""{"status":"no_account"}"""))).erase(token)

        assertEquals(EraseOutcome.NO_ACCOUNT, result.getOrNull())
    }

    @Test
    fun unknownStatus_isAFailure() = runTest {
        // A 200 whose body we cannot read is not permission to tell someone their account is
        // gone.
        val result = eraser(harness(MockResponse("""{"status":"pondering"}"""))).erase(token)

        assertIs<DomainError.AccountEraseFailed>(result.leftOrNull())
    }

    @Test
    fun staleVerification_asksForTheNumberAgain() = runTest {
        val result = eraser(
            harness(
                MockResponse(
                    """{"error_code":"stale_verification"}""",
                    status = HttpStatusCode.Unauthorized,
                ),
            ),
        ).erase(token)

        // The one code that leads somewhere different: the proof was too old, so the answer
        // is a fresh code rather than a retry of the erase.
        assertEquals(DomainError.ReVerificationRequired, result.leftOrNull())
    }

    @Test
    fun otherErrorCodes_areCarriedThroughAsIs() = runTest {
        val result = eraser(
            harness(
                MockResponse(
                    """{"error_code":"not_configured"}""",
                    status = HttpStatusCode.ServiceUnavailable,
                ),
            ),
        ).erase(token)

        // Kept verbatim: this ends up in a support conversation about an account that is
        // still there, and a paraphrase would lose the only clue.
        assertEquals(DomainError.AccountEraseFailed("not_configured"), result.leftOrNull())
    }

    @Test
    fun errorWithNoReadableBody_stillFailsCleanly() = runTest {
        val result = eraser(
            harness(MockResponse("<html>gateway timeout</html>", status = HttpStatusCode.BadGateway)),
        ).erase(token)

        assertEquals(DomainError.AccountEraseFailed(null), result.leftOrNull())
    }

    @Test
    fun aThrownRequest_comesBackAsAValue() = runTest {
        // Every method on the port promises an Either. A dropped connection throwing straight
        // out would crash the deletion screen instead of offering a retry.
        val harness = SupabaseTestHarness { throw RuntimeException("connection reset") }

        val result = eraser(harness).erase(token)

        assertIs<DomainError.AccountEraseFailed>(result.leftOrNull())
        assertEquals(1, harness.nonFatals.size)
    }
}
