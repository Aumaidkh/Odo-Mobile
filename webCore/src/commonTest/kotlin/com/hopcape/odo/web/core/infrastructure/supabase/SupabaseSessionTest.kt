package com.hopcape.odo.web.core.infrastructure.supabase

import com.hopcape.odo.web.core.platform.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SupabaseSessionTest {

    private class Store(override var refreshToken: String?) : TokenStore

    /**
     * GoTrue rotating the refresh token, modelled honestly.
     *
     * The first request for a given refresh token is answered with a new session;
     * every later request for that same, now-spent token is a 400. That is the
     * behaviour the real service has, and the behaviour that made two concurrent
     * refreshes sign the panel out.
     */
    private fun rotatingEngine(spent: MutableSet<String>, calls: MutableList<String>) = MockEngine { request ->
        val body = request.body.toString()
        val token = body.substringAfter("\"refresh_token\":\"").substringBefore('"')
        calls += token
        if (!spent.add(token)) {
            respond(
                """{"error":"invalid_grant","error_description":"Already Used"}""",
                HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        } else {
            respond(
                """{"access_token":"access-after-$token","refresh_token":"next-$token",""" +
                    """"expires_in":3600,"user":{"email":"admin@odoapp.in"}}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
    }

    private fun session(engine: MockEngine, store: TokenStore) = SupabaseSession(
        client = HttpClient(engine),
        baseUrl = "https://project.supabase.co",
        anonKey = "anon-key",
        tokens = store,
        sessionFunction = "admin-session",
    )

    @Test
    fun `concurrent callers spend the refresh token once`() = runTest {
        // The bug this pins. The panel's shell restores the session at startup
        // while the first screen's view model is already asking for a token. Both
        // reached the network, both spent the same refresh token, and the loser
        // read its 400 as "signed out" and cleared the session the winner had just
        // established. The tab stayed on screen looking signed in and made every
        // subsequent read as `anon` — which RLS answers `200 []`, so the audit log
        // drew "Nothing recorded yet" over a table with two hundred rows in it.
        val spent = mutableSetOf<String>()
        val calls = mutableListOf<String>()
        val store = Store("refresh-1")
        val session = session(rotatingEngine(spent, calls), store)

        val tokens = listOf(
            async { session.restore().getOrNull() },
            async { session.accessToken() },
            async { session.accessToken() },
        ).awaitAll()

        assertEquals(1, calls.size, "refreshed more than once: $calls")
        assertEquals("access-after-refresh-1", session.accessToken())
        assertEquals("next-refresh-1", store.refreshToken)
        // Nobody was handed a null, which is what the anonymous fallback fed on.
        tokens.forEach { assertNotNull(it) }
    }

    @Test
    fun `a token still in date is reused without a network call`() = runTest {
        val spent = mutableSetOf<String>()
        val calls = mutableListOf<String>()
        val session = session(rotatingEngine(spent, calls), Store("refresh-1"))

        session.accessToken()
        session.accessToken()
        session.accessToken()

        assertEquals(1, calls.size, "refreshed a token that had not expired: $calls")
    }

    @Test
    fun `no stored refresh token means signed out, not a failed refresh`() = runTest {
        val spent = mutableSetOf<String>()
        val calls = mutableListOf<String>()
        val session = session(rotatingEngine(spent, calls), Store(null))

        assertEquals(null, session.accessToken())
        assertEquals(0, calls.size)
    }
}
