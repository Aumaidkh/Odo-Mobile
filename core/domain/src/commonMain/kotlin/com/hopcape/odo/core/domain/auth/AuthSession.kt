package com.hopcape.odo.core.domain.auth

import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A signed-in session: who it belongs to, what proves it, and until when.
 *
 * In `:core:domain` rather than next to the auth provider because three things read it and
 * none of them should know who issued it — the sync gate asks whether there is one, every
 * outgoing request asks for the token, and every row written asks for the owner.
 *
 * Vendor-free by construction: two opaque strings, an id and a deadline. Nothing here says
 * Supabase, and swapping the provider changes only what fills it in.
 */
data class AuthSession(
    /** Sent as the bearer on an authenticated call. Short-lived. */
    val accessToken: String,
    /**
     * Renews [accessToken] when it runs out. Long-lived, and the reason a session is kept in
     * encrypted storage rather than anywhere else.
     */
    val refreshToken: String,
    /** Whose session this is. Becomes `owner_id` on every row they write. */
    val ownerId: OwnerId,
    /** When [accessToken] stops being accepted. */
    val expiresAt: Instant,
) {

    /**
     * Whether this should be renewed before being used again.
     *
     * Renewed early, not at the deadline: a token that expires mid-flight fails a request
     * that had no reason to fail, and a minute of slack costs nothing.
     */
    fun needsRefresh(now: Instant): Boolean = now >= expiresAt.minus(REFRESH_MARGIN)

    /**
     * Never print a session. Tokens in a log line are tokens in a bug report, and TDD §12
     * puts them in the same bucket as OTPs and bill contents.
     */
    override fun toString(): String = "AuthSession(owner=${ownerId.value}, expiresAt=$expiresAt)"

    private companion object {
        val REFRESH_MARGIN = 60.seconds
    }
}
