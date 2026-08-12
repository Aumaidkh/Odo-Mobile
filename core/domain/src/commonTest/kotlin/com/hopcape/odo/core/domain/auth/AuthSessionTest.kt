package com.hopcape.odo.core.domain.auth

import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * When a session counts as stale, and what it is allowed to say about itself.
 */
class AuthSessionTest {

    private val now = Instant.parse("2026-08-03T10:00:00Z")

    @Test
    fun aFreshSessionNeedsNoRefresh() {
        assertFalse(session(expiresIn = 30.minutes).needsRefresh(now))
    }

    @Test
    fun anExpiredSessionNeedsRefresh() {
        assertTrue(session(expiresIn = (-1).minutes).needsRefresh(now))
    }

    @Test
    fun aSessionAboutToExpireIsAlreadyStale() {
        // Renewed early, not at the deadline: a token that dies mid-flight fails a request
        // that had no reason to fail.
        assertTrue(session(expiresIn = 30.seconds).needsRefresh(now))
        assertTrue(session(expiresIn = 59.seconds).needsRefresh(now))
        assertFalse(session(expiresIn = 61.seconds).needsRefresh(now))
    }

    @Test
    fun printingASessionNeverPrintsItsTokens() {
        val printed = session(expiresIn = 30.minutes).toString()

        // Tokens in a log line are tokens in a bug report (TDD §12).
        assertFalse(printed.contains("access-secret"), printed)
        assertFalse(printed.contains("refresh-secret"), printed)
        assertTrue(printed.contains("owner-1"), printed)
    }

    private fun session(expiresIn: kotlin.time.Duration) = AuthSession(
        accessToken = "access-secret",
        refreshToken = "refresh-secret",
        ownerId = OwnerId("owner-1"),
        expiresAt = now.plus(expiresIn),
    )
}
