package com.hopcape.odo.web.core.infrastructure.firebase

import com.hopcape.odo.web.core.domain.WebError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirebaseSignInTest {

    private fun signIn(engine: MockEngine) = FirebaseSignIn(HttpClient(engine), apiKey = "test-key")

    private fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK) = MockEngine {
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    }

    @Test
    fun `the request asks for a refresh token`() = runTest {
        // The bug this pins, and it cost a day. `returnSecureToken` sits at its
        // default, and kotlinx-serialization does not write defaults unless told
        // to. Firebase honours the omission by answering 865 bytes instead of
        // 1377, with no refreshToken in them — and the parser then reports a
        // missing field, which points at the response rather than the request.
        //
        // No canned-response test can catch that. The mock hands back whatever it
        // is given, no matter what was asked. So this one looks at what was asked.
        var sent: String? = null
        val engine = MockEngine { request ->
            sent = (request.body as TextContent).text
            respond(
                """{"idToken":"id-1","email":"zahid@gmail.com"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        signIn(engine).identify("zahid@gmail.com", "hunter2")

        assertTrue(
            sent?.contains(""""returnSecureToken":true""") == true,
            "the request went out without returnSecureToken: $sent",
        )
    }

    @Test
    fun `a signed-in account comes back with its token`() = runTest {
        val identity = signIn(
            json("""{"idToken":"id-1","email":"zahid@gmail.com","displayName":"Zahid"}"""),
        ).identify("zahid@gmail.com", "hunter2").getOrNull()

        assertEquals("id-1", identity?.idToken)
        assertEquals("zahid@gmail.com", identity?.email)
        assertEquals("Zahid", identity?.displayName)
    }

    @Test
    fun `a wrong password and an unknown address are the same answer`() = runTest {
        // Telling them apart tells an attacker which addresses have accounts.
        val wrong = signIn(json("""{"error":{"message":"INVALID_LOGIN_CREDENTIALS"}}""", HttpStatusCode.BadRequest))
        val unknown = signIn(json("""{"error":{"message":"EMAIL_NOT_FOUND"}}""", HttpStatusCode.BadRequest))

        assertEquals(
            wrong.identify("a@b.com", "x").leftOrNull(),
            unknown.identify("c@d.com", "x").leftOrNull(),
        )
        assertEquals(WebError.SignInRejected(null), wrong.identify("a@b.com", "x").leftOrNull())
    }

    @Test
    fun `Firebase's own lockout is no tries left`() = runTest {
        assertEquals(
            WebError.SignInRejected(triesLeft = 0),
            signIn(json("""{"error":{"message":"TOO_MANY_ATTEMPTS_TRY_LATER"}}""", HttpStatusCode.BadRequest))
                .identify("a@b.com", "x").leftOrNull(),
        )
    }

    @Test
    fun `a project with password sign-in switched off says so`() = runTest {
        // Its own outcome, because nothing the person at the keyboard does fixes it.
        assertEquals(
            WebError.SignInUnavailable,
            signIn(json("""{"error":{"message":"PASSWORD_LOGIN_DISABLED"}}""", HttpStatusCode.BadRequest))
                .identify("a@b.com", "x").leftOrNull(),
        )
    }
}
