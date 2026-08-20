package com.hopcape.odo.web.blog.infrastructure

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.web.blog.domain.AuthRepository
import com.hopcape.odo.web.blog.domain.BlogError
import com.hopcape.odo.web.blog.domain.model.Session
import com.hopcape.odo.web.blog.platform.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Firebase Auth, over its REST API.
 *
 * **Why REST and not the Firebase JS SDK.** Kotlin/Wasm has no `@JsModule`, so
 * using the SDK means an npm dependency, a hand-written JavaScript shim to
 * re-export it onto `window`, and Kotlin externals to reach that. Password
 * sign-in over REST is three endpoints and no bundling at all. What the SDK
 * would have done for free — refreshing the token and remembering the session —
 * is the two small pieces of this class.
 *
 * That trade has one consequence worth knowing: the SDK's browser flows (Google,
 * phone) need the page's host to be on Firebase Auth's authorised-domains list,
 * and this path does not. Password sign-in from `odoapp.in` works without
 * touching that list. What does apply is any HTTP-referrer restriction on the
 * API key — it has to allow the blog's host, and for both hosts in
 * [FirebaseConfig], because refreshing goes to a different one.
 *
 * The account still has to be allowed to publish; see
 * [FirebaseConfig.AUTHOR_EMAILS] for what that check is and is not.
 */
@OptIn(ExperimentalTime::class)
class FirebaseAuthRepository(
    private val client: HttpClient,
    private val tokens: TokenStore,
    private val apiKey: String = FirebaseConfig.API_KEY,
    private val authors: Set<String> = FirebaseConfig.AUTHOR_EMAILS,
    private val now: () -> Instant = { Clock.System.now() },
) : AuthRepository {

    /**
     * The signed-in session, and the credential that proves it.
     *
     * In memory only. The refresh token is what survives a reload, and it is what
     * [TokenStore] keeps.
     */
    private var current: Authenticated? = null

    private data class Authenticated(
        val session: Session,
        val idToken: String,
        val expiresAt: Instant,
    )

    override suspend fun session(): Either<BlogError, Session?> {
        current?.let { return it.session.right() }

        // Nothing in memory. A refresh token from a previous page load is the
        // only thing that can bring a session back, and exchanging it is also how
        // we find out it has been revoked.
        val refreshToken = tokens.refreshToken ?: return null.right()
        return restore(refreshToken).fold(
            ifLeft = { error ->
                // A refresh token that no longer works is not an error to report —
                // it is simply somebody who has to sign in again. Anything else
                // (offline, a broken response) is worth surfacing.
                if (error is BlogError.NotSignedIn) {
                    tokens.refreshToken = null
                    null.right()
                } else {
                    error.left()
                }
            },
            ifRight = { it.session.right() },
        )
    }

    override suspend fun signIn(email: String, password: String): Either<BlogError, Session> {
        val response = post(
            url = "${FirebaseConfig.SIGN_IN_ENDPOINT}?key=$apiKey",
            body = REQUESTS.encodeToString(
                SignInRequest.serializer(),
                SignInRequest(email.trim(), password),
            ),
        ) ?: return BlogError.Offline.left()

        val payload = response.bodyAsText()
        if (!response.status.isSuccess()) return response.asError(payload).left()

        val body = decode(SignInResponse.serializer(), payload)
            ?: return BlogError.Unexpected("unreadable sign-in response").left()

        val address = body.email.ifBlank { email.trim() }
        if (!address.isAuthor()) {
            // Do not keep anything. A rejected account holding a live refresh
            // token would be signed in again by the next page load.
            tokens.refreshToken = null
            return refusal().left()
        }

        val authenticated = Authenticated(
            session = sessionOf(address, body.displayName),
            idToken = body.idToken,
            expiresAt = now() + (body.expiresIn.toLongOrNull() ?: DEFAULT_LIFETIME).seconds,
        )
        current = authenticated
        tokens.refreshToken = body.refreshToken
        return authenticated.session.right()
    }

    override suspend fun signOut(): Either<BlogError, Unit> {
        // Local only. Firebase has no "end this session" call for a password
        // sign-in — the ID token stays valid until it expires, which is why it is
        // short-lived and why the refresh token is the thing being thrown away.
        current = null
        tokens.refreshToken = null
        return Unit.right()
    }

    /**
     * A usable ID token, refreshed if the one held is close to expiring.
     *
     * Sixty seconds of headroom, because a token that expires between this call
     * and the request it is attached to is a failure nobody can reproduce.
     */
    suspend fun idToken(): Either<BlogError, String?> {
        val held = current ?: return session().map { current?.idToken }
        if (now() < held.expiresAt - REFRESH_HEADROOM) return held.idToken.right()
        val refreshToken = tokens.refreshToken ?: return BlogError.NotSignedIn.left()
        return restore(refreshToken).map { it.idToken }
    }

    /** Exchanges a refresh token for a live session. */
    private suspend fun restore(refreshToken: String): Either<BlogError, Authenticated> {
        val refreshed = post(
            url = "${FirebaseConfig.REFRESH_ENDPOINT}?key=$apiKey",
            body = REQUESTS.encodeToString(
                RefreshRequest.serializer(),
                RefreshRequest(refreshToken = refreshToken),
            ),
        ) ?: return BlogError.Offline.left()

        val refreshPayload = refreshed.bodyAsText()
        if (!refreshed.status.isSuccess()) {
            // Revoked, expired, or from a deleted account. All of them mean the
            // same thing to a reader: sign in again.
            return BlogError.NotSignedIn.left()
        }
        val tokenBody = decode(RefreshResponse.serializer(), refreshPayload)
            ?: return BlogError.Unexpected("unreadable refresh response").left()

        // The refresh response carries no email or name, so the account is read
        // once here rather than remembering fields across page loads that may be
        // stale by the time they are used.
        val looked = post(
            url = "${FirebaseConfig.LOOKUP_ENDPOINT}?key=$apiKey",
            body = REQUESTS.encodeToString(
                LookupRequest.serializer(),
                LookupRequest(idToken = tokenBody.idToken),
            ),
        ) ?: return BlogError.Offline.left()

        val lookupPayload = looked.bodyAsText()
        if (!looked.status.isSuccess()) return BlogError.NotSignedIn.left()
        val account = decode(LookupResponse.serializer(), lookupPayload)?.users?.firstOrNull()
            ?: return BlogError.NotSignedIn.left()

        if (!account.email.isAuthor()) {
            tokens.refreshToken = null
            return refusal().left()
        }

        val authenticated = Authenticated(
            session = sessionOf(account.email, account.displayName),
            idToken = tokenBody.idToken,
            expiresAt = now() + (tokenBody.expiresIn.toLongOrNull() ?: DEFAULT_LIFETIME).seconds,
        )
        current = authenticated
        // Firebase may hand back a rotated one; keeping the old would work until
        // the day it does rotate.
        tokens.refreshToken = tokenBody.refreshToken
        return authenticated.right()
    }

    private fun String.isAuthor(): Boolean =
        authors.any { it.equals(this, ignoreCase = true) }

    /**
     * Why an account with the right password was still turned away.
     *
     * An empty list refuses everybody, which is the safe way to be wrong and a
     * confusing way to be told about it — "this account is not allowed to
     * publish" reads as a decision somebody made about you. Saying which of the
     * two it is costs one branch.
     */
    private fun refusal(): BlogError =
        if (authors.isEmpty()) BlogError.NoAuthorsConfigured else BlogError.NotAnAuthor

    /**
     * The account, as the CMS talks about people.
     *
     * The slug comes from the display name when there is one, because it is what
     * an author page would live at; an email local-part is a fallback, not a
     * choice.
     */
    private fun sessionOf(email: String, displayName: String): Session {
        val name = displayName.ifBlank { email.substringBefore('@') }
        return Session(
            authorSlug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
            name = name,
            initial = name.firstOrNull()?.uppercase() ?: "?",
        )
    }

    /** Null when the request never left — offline, DNS, a blocked origin. */
    private suspend fun post(url: String, body: String): HttpResponse? =
        runCatching {
            client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }.getOrNull()

    /**
     * Maps Firebase's error strings onto the closed set the screens draw.
     *
     * Every credential failure collapses to one outcome on purpose: telling
     * somebody whether it was the address or the password that was wrong tells an
     * attacker which addresses have accounts.
     */
    private fun HttpResponse.asError(payload: String): BlogError {
        val message = decode(ErrorEnvelope.serializer(), payload)?.error?.message.orEmpty()
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

    private fun <T> decode(serializer: kotlinx.serialization.DeserializationStrategy<T>, payload: String): T? =
        runCatching { LENIENT.decodeFromString(serializer, payload) }.getOrNull()

    private companion object {
        /** Firebase's own default, used when the response omits it. */
        const val DEFAULT_LIFETIME = 3600L

        val REFRESH_HEADROOM = 60.seconds

        /**
         * Ignores fields we do not model. Firebase adds them, and a strict parser
         * would turn a successful sign-in into an unreadable response.
         */
        val LENIENT = Json { ignoreUnknownKeys = true }

        /**
         * Writes every field, including the ones sitting at their default.
         *
         * `encodeDefaults` is false by default, so `returnSecureToken = true` was
         * silently dropped from the sign-in request. Firebase honours that: it
         * answers without a refresh token, and the parser then reports
         * `refreshToken` missing — which reads as a wrong model, or a truncated
         * response, and sends you looking anywhere but at the request. The same
         * would have taken `grant_type` off every refresh.
         *
         * Sending a default explicitly is also just correct for a wire format
         * somebody else owns: the default is ours, not theirs.
         */
        val REQUESTS = Json { encodeDefaults = true }
    }
}

// ── Wire format ──────────────────────────────────────────────────────────────
// Only the fields this app reads. Everything else Firebase sends is ignored.

@Serializable
private data class SignInRequest(
    val email: String,
    val password: String,
    val returnSecureToken: Boolean = true,
)

@Serializable
private data class SignInResponse(
    val idToken: String,
    val refreshToken: String,
    val expiresIn: String = "",
    val email: String = "",
    val displayName: String = "",
)

@Serializable
private data class RefreshRequest(
    @SerialName("grant_type") val grantType: String = "refresh_token",
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
private data class RefreshResponse(
    @SerialName("id_token") val idToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: String = "",
)

@Serializable
private data class LookupRequest(val idToken: String)

@Serializable
private data class LookupResponse(val users: List<Account> = emptyList())

@Serializable
private data class Account(
    val email: String = "",
    val displayName: String = "",
)

@Serializable
private data class ErrorEnvelope(val error: ErrorBody? = null)

@Serializable
private data class ErrorBody(val message: String = "")
