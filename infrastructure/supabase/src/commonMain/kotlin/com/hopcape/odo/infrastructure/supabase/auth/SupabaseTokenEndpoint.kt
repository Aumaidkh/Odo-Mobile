package com.hopcape.odo.infrastructure.supabase.auth

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.auth.AuthSession
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.infrastructure.supabase.SupabaseEnvironment
import com.hopcape.odo.infrastructure.supabase.http.SupabaseJson
import com.hopcape.odo.infrastructure.supabase.observability.SupabaseTelemetry
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Everything that mints or ends a session, in one place.
 *
 * Both gateways sit on this — the difference between real phone sign-in and the development
 * password account is which endpoint they call, not which client. What is shared lives here:
 * turning a token response into an [AuthSession], and turning a failure into a `DomainError`
 * the screens can act on.
 *
 * Mostly GoTrue, with one exception. `firebase-session` is an Edge Function rather than part
 * of GoTrue, because trading a Firebase ID token for a Supabase session is this project's own
 * arrangement and not something GoTrue offers. It answers in GoTrue's token shape, so it
 * shares everything below anyway.
 *
 * **Error bodies are never logged.** GoTrue echoes the phone number or email that failed, and
 * TDD §12 puts identifiers in the same bucket as tokens — only the status is read.
 */
internal class SupabaseTokenEndpoint(
    private val client: HttpClient,
    private val environment: SupabaseEnvironment,
    private val telemetry: SupabaseTelemetry,
    private val clock: Clock = Clock.System,
) {

    /**
     * `POST /functions/v1/firebase-session` — a verified phone number for a session.
     *
     * The one call here that is not GoTrue. Firebase proves the number but cannot issue the
     * session, because `owner_id` is a `uuid` referencing `auth.users(id)` and a Firebase UID
     * is not one; the function finds or creates the matching user and mints an ordinary
     * session for it. It answers in GoTrue's own token shape, which is why [toSession] reads
     * it unchanged.
     *
     * A 401 means the proof is no longer good — the ID token expired between the code screen
     * and here, or it was issued for another project. The answer is a new code, not a
     * retyped one, so it maps to [DomainError.OtpExpired].
     */
    suspend fun exchangeFirebaseToken(idToken: String): Either<DomainError, AuthSession> =
        attempt(OP_FIREBASE_EXCHANGE, DomainError.OtpRequestFailed) {
            post(
                "firebase-session",
                JsonObject(mapOf("idToken" to JsonPrimitive(idToken))),
                base = environment.functionsUrl,
            ).toSession { response ->
                if (response.status.value == UNAUTHORIZED) DomainError.OtpExpired
                else DomainError.OtpRequestFailed
            }
        }

    /** `POST /auth/v1/token?grant_type=password` — the development account's way in. */
    suspend fun password(email: String, password: String): Either<DomainError, AuthSession> =
        attempt(OP_PASSWORD, DomainError.OtpRequestFailed) {
            post(
                "token?grant_type=password",
                JsonObject(mapOf("email" to JsonPrimitive(email), "password" to JsonPrimitive(password))),
            ).toSession { DomainError.OtpRequestFailed }
        }

    /**
     * `POST /auth/v1/token?grant_type=refresh_token`.
     *
     * **Only a refusal is terminal.** GoTrue answering 400 or 401 means the token is revoked
     * or past renewal, and no amount of retrying changes that — [DomainError.SessionExpired],
     * and the caller signs out. Anything else is about this moment rather than about the
     * token: a 5xx, a rate limit, a request that never got an answer at all. Those map to
     * [DomainError.SessionUnavailable] and the session stays.
     *
     * Both used to be `SessionExpired`, including the timeout, so a renewal attempted on a
     * flaky connection ended the session outright — quietly, with the app still working and
     * nothing syncing (issue #312).
     */
    suspend fun refresh(refreshToken: String): Either<DomainError, AuthSession> =
        attempt(OP_REFRESH, DomainError.SessionUnavailable) {
            post(
                "token?grant_type=refresh_token",
                JsonObject(mapOf("refresh_token" to JsonPrimitive(refreshToken))),
            ).toSession { response ->
                if (response.status.value in TERMINAL_REFRESH_STATUSES) DomainError.SessionExpired
                else DomainError.SessionUnavailable
            }
        }

    /** `POST /auth/v1/logout`. Best effort — the caller clears local state regardless. */
    suspend fun signOut(accessToken: String): Either<DomainError, Unit> =
        attempt(OP_SIGN_OUT, DomainError.SessionExpired) {
            val response = post("logout", JsonObject(emptyMap()), bearer = accessToken)
            if (response.status.isSuccess()) Unit.right() else DomainError.SessionExpired.left()
        }

    /**
     * Span [block], and make sure a request that never got an answer comes back as a value.
     *
     * Every method here promises an `Either`. Without this, a timeout or a dropped
     * connection throws straight out of the gateway and past the port's contract — which on
     * the OTP screen means a crash on a flaky train instead of "couldn't send the code".
     *
     * Cancellation is rethrown: a screen the owner left is not a failure.
     */
    private suspend fun <T> attempt(
        operation: String,
        onUnreachable: DomainError,
        block: suspend () -> Either<DomainError, T>,
    ): Either<DomainError, T> = telemetry.span(operation, RESOURCE) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            telemetry.failed(operation, RESOURCE, e)
            onUnreachable.left()
        }
    }

    /**
     * [base] defaults to GoTrue, which every call here but one uses. The exception is the
     * Firebase exchange, which is an Edge Function and lives under a different path.
     */
    private suspend fun post(
        path: String,
        body: JsonObject,
        bearer: String? = null,
        base: String = environment.authUrl,
    ): HttpResponse =
        client.post("$base/$path") {
            contentType(ContentType.Application.Json)
            bearer?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(SupabaseJson.encodeToString(JsonObject.serializer(), body))
        }

    /** A successful token response as a domain session, or [onFailure]'s error. */
    private suspend fun HttpResponse.toSession(
        onFailure: suspend (HttpResponse) -> DomainError,
    ): Either<DomainError, AuthSession> {
        if (!status.isSuccess()) {
            telemetry.rejected(OP_TOKEN, RESOURCE, status.value)
            return onFailure(this).left()
        }
        val token = SupabaseJson.decodeFromString(TokenResponse.serializer(), bodyAsText())
        return AuthSession(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            ownerId = OwnerId(token.user.id),
            // The server sends a lifetime, not a deadline. Turning it into one here means
            // everything downstream compares instants instead of doing arithmetic against
            // whenever it happens to be asked.
            expiresAt = clock.now().plus(token.expiresIn.seconds),
        ).right()
    }

    private companion object {
        const val RESOURCE = "auth"
        const val OP_PASSWORD = "auth.password"
        const val OP_FIREBASE_EXCHANGE = "auth.firebase.exchange"
        const val OP_REFRESH = "auth.refresh"
        const val OP_SIGN_OUT = "auth.signOut"
        const val OP_TOKEN = "auth.token"

        const val UNAUTHORIZED = 401

        /**
         * The statuses that mean the refresh token itself is no good. Everything else — 429,
         * any 5xx, anything unexpected — is treated as a bad moment rather than a verdict.
         */
        val TERMINAL_REFRESH_STATUSES = setOf(400, 401, 403)
    }
}

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("user") val user: TokenUser,
)

@Serializable
private data class TokenUser(@SerialName("id") val id: String)
