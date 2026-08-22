package com.hopcape.odo.web.blog.infrastructure.firebase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.web.blog.domain.BlogError
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Who Firebase says this is, and the token that proves it to somebody else. */
data class FirebaseIdentity(val idToken: String, val email: String, val displayName: String)

/**
 * Password sign-in against Firebase, and nothing more.
 *
 * It used to decide who was allowed to publish as well. That check now lives in
 * the `blog-session` edge function, where a browser cannot reach it — a client
 * gate is a courtesy, not a control. What is left here is one question: does this
 * address and password belong to an account in our Firebase project?
 *
 * Over REST rather than the Firebase JS SDK: Kotlin/Wasm has no `@JsModule`, so
 * the SDK would need an npm dependency, a JavaScript shim and externals to reach
 * it. This is one endpoint.
 */
internal class FirebaseSignIn(
    private val client: HttpClient,
    private val apiKey: String,
) {

    suspend fun identify(email: String, password: String): Either<BlogError, FirebaseIdentity> {
        val response = runCatching {
            client.post("$ENDPOINT?key=$apiKey") {
                contentType(ContentType.Application.Json)
                // encodeDefaults, because `returnSecureToken` sits at its default
                // and kotlinx-serialization drops those unless told otherwise.
                // Firebase honours the omission by answering without a refresh
                // token, and the failure that follows names the wrong thing.
                setBody(REQUESTS.encodeToString(Request.serializer(), Request(email.trim(), password)))
            }
        }.getOrNull() ?: return BlogError.Offline.left()

        val payload = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
        if (!response.status.isSuccess()) return response.asError(payload).left()

        val body = runCatching { LENIENT.decodeFromString(Response.serializer(), payload) }.getOrNull()
            ?: return BlogError.Unexpected("unreadable sign-in response").left()

        return FirebaseIdentity(
            idToken = body.idToken,
            email = body.email.ifBlank { email.trim() },
            displayName = body.displayName,
        ).right()
    }

    /**
     * Firebase's error strings, onto the closed set the screens draw.
     *
     * Every credential failure collapses to one outcome. Telling somebody whether
     * it was the address or the password that was wrong tells an attacker which
     * addresses have accounts.
     */
    private fun HttpResponse.asError(payload: String): BlogError {
        val message = runCatching { LENIENT.decodeFromString(ErrorEnvelope.serializer(), payload) }
            .getOrNull()?.error?.message.orEmpty()
        return when {
            message.startsWith("PASSWORD_LOGIN_DISABLED") ||
                message.startsWith("OPERATION_NOT_ALLOWED") -> BlogError.SignInUnavailable

            // Firebase's own lockout. Zero is what the design draws as "no tries
            // left", which is exactly what this is.
            message.startsWith("TOO_MANY_ATTEMPTS_TRY_LATER") ||
                message.startsWith("USER_DISABLED") -> BlogError.SignInRejected(triesLeft = 0)

            message.startsWith("EMAIL_NOT_FOUND") ||
                message.startsWith("INVALID_PASSWORD") ||
                message.startsWith("INVALID_LOGIN_CREDENTIALS") ||
                message.startsWith("INVALID_EMAIL") ||
                message.startsWith("MISSING_PASSWORD") ->
                // Null, not a number: Firebase does not say how many attempts are
                // left, and the screen drops the count rather than inventing one.
                BlogError.SignInRejected(triesLeft = null)

            else -> BlogError.Unexpected(message.ifBlank { "HTTP $status" })
        }
    }

    private companion object {
        const val ENDPOINT = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword"
        val LENIENT = Json { ignoreUnknownKeys = true }
        val REQUESTS = Json { encodeDefaults = true }
    }
}

@Serializable
private data class Request(
    val email: String,
    val password: String,
    val returnSecureToken: Boolean = true,
)

@Serializable
private data class Response(
    val idToken: String,
    val email: String = "",
    val displayName: String = "",
)

@Serializable
private data class ErrorEnvelope(val error: ErrorBody? = null)

@Serializable
private data class ErrorBody(val message: String = "")
