package com.hopcape.odo.web.admin.infrastructure

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.infrastructure.supabase.SupabaseSession
import com.hopcape.odo.web.core.infrastructure.supabase.jsonEscaped
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
 * `admin-invite` over HTTP.
 *
 * An edge function rather than a table, because the work is not in Postgres: it
 * creates a Firebase Auth account and asks Firebase to send a password-reset link.
 * Neither is something a browser should be trusted to do on its own — the function
 * checks `admin.roles.write` first, and refuses any address that is not already on
 * the allowlist.
 */
internal class SupabaseInvites(
    private val client: HttpClient,
    private val baseUrl: String,
    private val anonKey: String,
    private val session: SupabaseSession,
) {

    suspend fun invite(email: String): Either<WebError, Unit> {
        val token = session.accessToken() ?: return WebError.NotSignedIn.left()
        val response = runCatching {
            client.post("$baseUrl/functions/v1/$FUNCTION") {
                header("apikey", anonKey)
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"email":"${email.jsonEscaped()}"}""")
            }
        }.getOrNull() ?: return WebError.Offline.left()

        if (response.status.isSuccess()) return Unit.right()

        val body = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
        // The function's own vocabulary, kept apart where the difference changes what
        // somebody should do next. A project with email/password sign-up switched off
        // and an unreachable mail server are both "it did not work", and telling them
        // apart is the difference between one console toggle and a support ticket.
        return WebError.Unexpected("admin-invite ${response.status.value}: ${body.take(160)}").left()
    }

    private companion object {
        const val FUNCTION = "admin-invite"
    }
}
