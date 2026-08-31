package com.hopcape.odo.web.core.infrastructure.supabase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.platform.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
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
 * The Supabase half of signing in.
 *
 * Firebase proves who somebody is; it cannot issue a Supabase session, and every
 * policy in the schema is written against `auth.jwt()`. A session edge function is
 * the join: it checks Firebase's signature, checks the address is allowed in, and
 * mints an ordinary GoTrue session.
 *
 * Which function that is comes in as [sessionFunction], because there is one per
 * audience and they are deliberately not the same function — `blog-session` checks
 * an author list, `admin-session` checks the staff table. Folding them together
 * would mean one function where a bug in one path hands out the other's access.
 *
 * After that exchange this is an ordinary Supabase client. The Firebase token is
 * not kept — what survives a reload is the GoTrue refresh token, because that is
 * what every subsequent request is actually authorised by.
 *
 * **The permission check is not here.** It is in the function, where a browser
 * cannot reach it. A 403 from the exchange is the only answer this class needs.
 */
@OptIn(ExperimentalTime::class)
class SupabaseSession(
    private val client: HttpClient,
    private val baseUrl: String,
    private val anonKey: String,
    private val tokens: TokenStore,
    /** The edge function that mints the session: `blog-session`, `admin-session`. */
    private val sessionFunction: String,
    private val now: () -> Instant = { Clock.System.now() },
) {

    private var accessToken: String? = null
    private var expiresAt: Instant? = null

    /**
     * The address the session belongs to.
     *
     * Read off the token response rather than the stored refresh token, because
     * the refresh token says nothing about who it is for. It is what the subject
     * row is looked up by.
     */
    private var email: String? = null

    /**
     * The application row this session belongs to — the `blog_authors` row for the
     * CMS, the `admin_users` row for the panel.
     *
     * Not the `auth.users` id: that is in the token. This is the id of whatever row
     * the product hangs off an account, set once it has been read, so a caller can
     * scope a query to "mine" without reading it again. RLS already refuses
     * everything it should on write; this is so a list does not also show
     * everybody else's rows.
     */
    var subjectId: String? = null

    /** True once there is a session, without going near the network to find out. */
    val isActive: Boolean get() = accessToken != null

    fun email(): String? = email

    /**
     * A usable access token, refreshed when it is close to running out.
     *
     * Null means signed out. Sixty seconds of headroom, because a token that
     * expires between this call and the request it is attached to is a failure
     * nobody can reproduce.
     */
    suspend fun accessToken(): String? {
        val held = accessToken
        val expiry = expiresAt
        if (held != null && expiry != null && now() < expiry - HEADROOM) return held
        return restore().getOrNull()?.let { accessToken }
    }

    /** Trades a Firebase ID token for a session. The one call that needs Firebase. */
    suspend fun exchange(firebaseIdToken: String): Either<WebError, String> {
        val response = runCatching {
            client.post("$baseUrl/functions/v1/$sessionFunction") {
                header("apikey", anonKey)
                contentType(ContentType.Application.Json)
                setBody("""{"idToken":"$firebaseIdToken"}""")
            }
        }.getOrNull() ?: return WebError.Offline.left()

        val body = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
        if (!response.status.isSuccess()) {
            return when (response.status) {
                // The function's own verdict on the address. Its own outcome,
                // because it is not a wrong password and telling somebody it was
                // sends them round a loop that cannot end.
                HttpStatusCode.Forbidden -> WebError.NotPermitted
                HttpStatusCode.Unauthorized -> WebError.SignInRejected(triesLeft = null)
                else -> WebError.Unexpected("$sessionFunction ${response.status.value}")
            }.left()
        }
        return adopt(body)
    }

    /**
     * Brings a session back from the stored refresh token.
     *
     * A token that no longer works is not an error to report — it is somebody who
     * has to sign in again, which is what [WebError.NotSignedIn] means here.
     */
    suspend fun restore(): Either<WebError, String?> {
        val refreshToken = tokens.refreshToken ?: return null.right()
        val response = runCatching {
            client.post("$baseUrl/auth/v1/token?grant_type=refresh_token") {
                header("apikey", anonKey)
                contentType(ContentType.Application.Json)
                setBody("""{"refresh_token":"$refreshToken"}""")
            }
        }.getOrNull() ?: return WebError.Offline.left()

        val body = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
        if (!response.status.isSuccess()) {
            clear()
            return null.right()
        }
        return adopt(body).map { it }
    }

    fun clear() {
        accessToken = null
        expiresAt = null
        subjectId = null
        tokens.refreshToken = null
    }

    /** Takes a GoTrue token response and becomes signed in. */
    private fun adopt(body: String): Either<WebError, String> {
        val session = runCatching { LENIENT.decodeFromString(TokenResponse.serializer(), body) }
            .getOrNull() ?: return WebError.Unexpected("unreadable session response").left()
        accessToken = session.accessToken
        expiresAt = now() + session.expiresIn.seconds
        session.user?.email?.let { email = it }
        // GoTrue rotates the refresh token on every use; keeping the old one works
        // until the day it does not.
        tokens.refreshToken = session.refreshToken
        return session.accessToken.right()
    }

    private companion object {
        val HEADROOM = 60.seconds
        val LENIENT = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    val user: TokenUser? = null,
)

@Serializable
private data class TokenUser(val email: String? = null)
