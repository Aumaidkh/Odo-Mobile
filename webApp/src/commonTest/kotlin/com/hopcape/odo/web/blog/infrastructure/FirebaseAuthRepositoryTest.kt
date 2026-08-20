package com.hopcape.odo.web.blog.infrastructure

import com.hopcape.odo.web.blog.domain.BlogError
import com.hopcape.odo.web.blog.platform.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Firebase adapter, driven by canned responses.
 *
 * Every case here is one Firebase actually returns. The point is not that the
 * HTTP works — Ktor's does — but that each of Firebase's error strings lands on
 * the one outcome the screen knows how to draw, and that a rejected account
 * never leaves a usable token behind.
 */
class FirebaseAuthRepositoryTest {

    private class MemoryTokens(override var refreshToken: String? = null) : TokenStore

    private fun repository(
        engine: MockEngine,
        tokens: TokenStore = MemoryTokens(),
        authors: Set<String> = setOf("rahul@odoapp.in"),
    ) = FirebaseAuthRepository(
        client = HttpClient(engine),
        tokens = tokens,
        apiKey = "test-key",
        authors = authors,
    )

    private fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK) = MockEngine { _ ->
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    }

    @Test
    fun `a signed-in author gets a session and a stored refresh token`() = runTest {
        val tokens = MemoryTokens()
        val auth = repository(
            tokens = tokens,
            engine = json(
                """
                {"idToken":"id-1","refreshToken":"refresh-1","expiresIn":"3600",
                 "email":"rahul@odoapp.in","displayName":"Rahul Deshmukh"}
                """.trimIndent(),
            ),
        )

        val session = auth.signIn("rahul@odoapp.in", "hunter2").getOrNull()

        assertEquals("Rahul Deshmukh", session?.name)
        assertEquals("rahul-deshmukh", session?.authorSlug)
        assertEquals("R", session?.initial)
        assertEquals("refresh-1", tokens.refreshToken)
    }

    @Test
    fun `the sign-in request asks for a refresh token`() = runTest {
        // The bug this pins: `returnSecureToken` sits at its default, and
        // kotlinx-serialization does not write defaults unless told to. Firebase
        // honours the omission and answers without a refresh token, at which point
        // the parser reports a missing field and every finger points at the
        // response. Nothing in a canned-response test can catch that — the request
        // is what has to be looked at.
        var sent: String? = null
        val engine = MockEngine { request ->
            sent = (request.body as TextContent).text
            respond(
                """{"idToken":"id-1","refreshToken":"refresh-1","email":"rahul@odoapp.in"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        repository(engine = engine).signIn("rahul@odoapp.in", "hunter2")

        assertTrue(
            sent?.contains(""""returnSecureToken":true""") == true,
            "the request went out without returnSecureToken: $sent",
        )
    }

    @Test
    fun `the refresh request carries its grant type`() = runTest {
        // Restoring a session is two calls — refresh, then look the account up —
        // so every body is collected and the assertion names the one it means.
        // Keeping only the last would silently check the wrong request.
        val sent = mutableListOf<String>()
        var call = 0
        val engine = MockEngine { request ->
            sent += (request.body as TextContent).text
            val body = if (call++ == 0) {
                """{"id_token":"id-2","refresh_token":"refresh-2"}"""
            } else {
                """{"users":[{"email":"rahul@odoapp.in","displayName":"Rahul"}]}"""
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }

        repository(engine = engine, tokens = MemoryTokens(refreshToken = "refresh-1")).session()

        // Same trap as the sign-in request: grant_type sits at its default.
        assertTrue(
            sent.firstOrNull()?.contains(""""grant_type":"refresh_token"""") == true,
            "the refresh went out without a grant type: ${sent.firstOrNull()}",
        )
    }

    @Test
    fun `the exact response Firebase sends is readable`() = runTest {
        // Copied from a real signInWithPassword call, extra keys and all.
        val tokens = MemoryTokens()
        val auth = repository(
            tokens = tokens,
            authors = setOf("zahid@gmail.com"),
            engine = json(
                """
                {"kind":"identitytoolkit#VerifyPasswordResponse",
                 "localId":"wfqGt82KTPhqAr2ZKFvuxYpMwFg2",
                 "email":"zahid@gmail.com",
                 "displayName":"",
                 "idToken":"eyJhbGciOiJSUzI1NiJ9.payload.signature",
                 "registered":true,
                 "refreshToken":"AMf-vByIbdUQdOcxD8mpl8SYfFN56qFZ",
                 "expiresIn":"3600"}
                """.trimIndent(),
            ),
        )

        val result = auth.signIn("zahid@gmail.com", "12341234")

        assertTrue(result.isRight(), "real payload was rejected: ${result.leftOrNull()}")
        assertEquals("zahid", result.getOrNull()?.name)
    }

    @Test
    fun `an empty author list says so instead of blaming the account`() = runTest {
        val auth = repository(
            authors = emptySet(),
            engine = json("""{"idToken":"id-1","refreshToken":"refresh-1","email":"anyone@odoapp.in"}"""),
        )
        assertEquals(
            com.hopcape.odo.web.blog.domain.BlogError.NoAuthorsConfigured,
            auth.signIn("anyone@odoapp.in", "hunter2").leftOrNull(),
        )
    }

    @Test
    fun `an account that is not an author is refused and leaves nothing behind`() = runTest {
        val tokens = MemoryTokens()
        val auth = repository(
            tokens = tokens,
            authors = setOf("someone-else@odoapp.in"),
            engine = json(
                """{"idToken":"id-1","refreshToken":"refresh-1","email":"reader@example.com"}""",
            ),
        )

        assertEquals(BlogError.NotAnAuthor, auth.signIn("reader@example.com", "hunter2").leftOrNull())
        assertNull(
            tokens.refreshToken,
            "a refused account must not keep a refresh token, or the next page load signs it back in",
        )
    }

    @Test
    fun `a wrong password does not say which half was wrong`() = runTest {
        val auth = repository(
            engine = json("""{"error":{"message":"INVALID_LOGIN_CREDENTIALS"}}""", HttpStatusCode.BadRequest),
        )
        assertEquals(
            BlogError.SignInRejected(triesLeft = null),
            auth.signIn("rahul@odoapp.in", "wrong").leftOrNull(),
        )
    }

    @Test
    fun `an unknown address is the same outcome as a wrong password`() = runTest {
        val auth = repository(
            engine = json("""{"error":{"message":"EMAIL_NOT_FOUND"}}""", HttpStatusCode.BadRequest),
        )
        // Telling them apart would tell an attacker which addresses have accounts.
        assertEquals(
            BlogError.SignInRejected(triesLeft = null),
            auth.signIn("nobody@odoapp.in", "hunter2").leftOrNull(),
        )
    }

    @Test
    fun `Firebase's own lockout is no tries left`() = runTest {
        val auth = repository(
            engine = json("""{"error":{"message":"TOO_MANY_ATTEMPTS_TRY_LATER"}}""", HttpStatusCode.BadRequest),
        )
        assertEquals(
            BlogError.SignInRejected(triesLeft = 0),
            auth.signIn("rahul@odoapp.in", "hunter2").leftOrNull(),
        )
    }

    @Test
    fun `a project with password sign-in switched off says so`() = runTest {
        // The state the production project is in as this is written.
        val auth = repository(
            engine = json("""{"error":{"message":"PASSWORD_LOGIN_DISABLED"}}""", HttpStatusCode.BadRequest),
        )
        assertEquals(BlogError.SignInUnavailable, auth.signIn("rahul@odoapp.in", "hunter2").leftOrNull())
    }

    @Test
    fun `no stored token means signed out, not an error`() = runTest {
        val auth = repository(engine = json("{}"))
        assertEquals(null, auth.session().getOrNull())
    }

    @Test
    fun `a stored refresh token brings the session back`() = runTest {
        val tokens = MemoryTokens(refreshToken = "refresh-1")
        var call = 0
        val engine = MockEngine { _ ->
            call++
            val body = if (call == 1) {
                """{"id_token":"id-2","refresh_token":"refresh-2","expires_in":"3600"}"""
            } else {
                """{"users":[{"email":"rahul@odoapp.in","displayName":"Rahul Deshmukh"}]}"""
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }

        val session = repository(tokens = tokens, engine = engine).session().getOrNull()

        assertEquals("Rahul Deshmukh", session?.name)
        // Firebase may rotate it; keeping the old one works until the day it does.
        assertEquals("refresh-2", tokens.refreshToken)
    }

    @Test
    fun `a revoked refresh token signs out quietly`() = runTest {
        val tokens = MemoryTokens(refreshToken = "revoked")
        val auth = repository(
            tokens = tokens,
            engine = MockEngine { respondError(HttpStatusCode.BadRequest) },
        )

        val result = auth.session()

        assertTrue(result.isRight(), "a dead token is somebody who has to sign in again, not a failure to report")
        assertNull(result.getOrNull())
        assertNull(tokens.refreshToken)
    }

    @Test
    fun `signing out throws the refresh token away`() = runTest {
        val tokens = MemoryTokens(refreshToken = "refresh-1")
        val auth = repository(tokens = tokens, engine = json("{}"))
        auth.signOut()
        assertNull(tokens.refreshToken)
        assertNull(auth.session().getOrNull())
    }
}
