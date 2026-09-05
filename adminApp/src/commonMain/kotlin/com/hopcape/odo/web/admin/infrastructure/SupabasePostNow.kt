package com.hopcape.odo.web.admin.infrastructure

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.infrastructure.supabase.SupabaseSession
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * "Make one now" — the only edge function this section calls.
 *
 * A function rather than an RPC because it does three things Postgres cannot: asks Gemini for
 * copy, and asks GitHub to run the renderer. The caller's own session travels with it, so the
 * permission is checked against the person pressing the button rather than against a key.
 */
internal class SupabasePostNow(
    private val client: HttpClient,
    private val baseUrl: String,
    private val anonKey: String,
    private val session: SupabaseSession,
) {

    suspend fun postNow(): Either<WebError, String> {
        val token = session.accessToken() ?: return WebError.NotSignedIn.left()
        val response = runCatching {
            client.post("$baseUrl/functions/v1/$FUNCTION") {
                header("apikey", anonKey)
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
        }.getOrNull() ?: return WebError.Offline.left()

        val body = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
        if (response.status.isSuccess()) return body.right()

        // The function's own words. A paused pipeline and a missing Gemini key are both "it
        // did not work", and which one it was decides what to do next.
        return WebError.Unexpected("post-now ${response.status.value}: ${body.take(160)}").left()
    }

    private companion object {
        const val FUNCTION = "post-now"
    }
}
