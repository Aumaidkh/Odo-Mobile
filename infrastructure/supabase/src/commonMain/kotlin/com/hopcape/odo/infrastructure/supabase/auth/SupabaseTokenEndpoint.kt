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
 * GoTrue's HTTP surface, in one place.
 *
 * Both gateways sit on this — the difference between phone OTP and the development password
 * account is two endpoints, not two clients. Everything shared lives here: turning a token
 * response into an [AuthSession], and turning GoTrue's error shapes into `DomainError`s the
 * screens can act on.
 *
 * **Error bodies are read but never logged.** GoTrue echoes the phone number or email that
 * failed, and TDD §12 puts OTPs and identifiers in the same bucket as tokens. The
 * `error_code` is extracted, the rest is dropped.
 */
internal class SupabaseTokenEndpoint(
    private val client: HttpClient,
    private val environment: SupabaseEnvironment,
    private val telemetry: SupabaseTelemetry,
    private val clock: Clock = Clock.System,
) {

    /** `POST /auth/v1/otp` — ask GoTrue to send a code. */
    suspend fun sendOtp(phone: String): Either<DomainError, Unit> =
        attempt(OP_OTP_SEND, DomainError.OtpRequestFailed) {
            val response = post("otp", JsonObject(mapOf("phone" to JsonPrimitive(phone))))
            if (response.status.isSuccess()) Unit.right() else response.toOtpError().left()
        }

    /** `POST /auth/v1/verify` — exchange a code for a session. */
    suspend fun verifyOtp(phone: String, code: String): Either<DomainError, AuthSession> =
        attempt(OP_OTP_VERIFY, DomainError.OtpRequestFailed) {
            post(
                "verify",
                JsonObject(
                    mapOf(
                        "type" to JsonPrimitive(SMS_TYPE),
                        "phone" to JsonPrimitive(phone),
                        "token" to JsonPrimitive(code),
                    ),
                ),
            ).toSession { it.toOtpError() }
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
     * A rejection here is terminal — the token is revoked or past renewal — so it maps to
     * [DomainError.SessionExpired] rather than something retryable.
     */
    suspend fun refresh(refreshToken: String): Either<DomainError, AuthSession> =
        attempt(OP_REFRESH, DomainError.SessionExpired) {
            post(
                "token?grant_type=refresh_token",
                JsonObject(mapOf("refresh_token" to JsonPrimitive(refreshToken))),
            ).toSession { DomainError.SessionExpired }
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

    private suspend fun post(path: String, body: JsonObject, bearer: String? = null): HttpResponse =
        client.post("${environment.authUrl}/$path") {
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

    /**
     * GoTrue's failure, as something the OTP screen can act on.
     *
     * The three that matter are told apart because the answer differs: retype the code,
     * ask for a new one, or wait. Everything else is "it did not work".
     */
    private suspend fun HttpResponse.toOtpError(): DomainError {
        telemetry.rejected(OP_TOKEN, RESOURCE, status.value)
        if (status.value == TOO_MANY_REQUESTS) {
            val retryAfter = headers[HttpHeaders.RetryAfter]?.toLongOrNull() ?: DEFAULT_RETRY_SECONDS
            return DomainError.TooManyOtpRequests(retryAfter)
        }
        return when (errorCode()) {
            CODE_OTP_EXPIRED -> DomainError.OtpExpired
            CODE_OTP_INVALID, CODE_BAD_GRANT -> DomainError.InvalidOtp
            else -> DomainError.OtpRequestFailed
        }
    }

    /** Only the machine-readable code. The rest of the body quotes the phone number. */
    private suspend fun HttpResponse.errorCode(): String? =
        runCatching {
            SupabaseJson.decodeFromString(ErrorResponse.serializer(), bodyAsText()).errorCode
        }.getOrNull()

    private companion object {
        const val RESOURCE = "auth"
        const val OP_OTP_SEND = "auth.otp.send"
        const val OP_OTP_VERIFY = "auth.otp.verify"
        const val OP_PASSWORD = "auth.password"
        const val OP_REFRESH = "auth.refresh"
        const val OP_SIGN_OUT = "auth.signOut"
        const val OP_TOKEN = "auth.token"

        const val SMS_TYPE = "sms"
        const val TOO_MANY_REQUESTS = 429
        const val DEFAULT_RETRY_SECONDS = 60L

        const val CODE_OTP_EXPIRED = "otp_expired"
        const val CODE_OTP_INVALID = "otp_disabled"
        const val CODE_BAD_GRANT = "invalid_grant"
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

@Serializable
private data class ErrorResponse(@SerialName("error_code") val errorCode: String? = null)
