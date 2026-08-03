package com.hopcape.odo.feature.auth.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.auth.AuthGateway
import com.hopcape.odo.core.domain.auth.AuthSession
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.platform.secure.SecureStore
import com.hopcape.odo.feature.auth.AuthTelemetry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The session's lifecycle: keep it, renew it before it dies, and give up cleanly when it
 * cannot be renewed.
 */
class OdoSessionManagerTest {

    private val now = Instant.parse("2026-08-03T10:00:00Z")
    private val phone = PhoneNumber.of("9812345678").getOrNull()!!

    @Test
    fun verifyingACodeKeepsTheSessionOnDiskAndInMemory() = runTest {
        val store = InMemoryStore()
        val manager = manager(store = store)

        manager.verifyOtp(phone, "123456")

        assertTrue(manager.isSignedIn())
        assertEquals(OwnerId("user-1"), manager.currentOwnerId())
        // Persisted, so a relaunch does not need the network.
        assertEquals("refresh-1", store.get(SecureStore.KEY_REFRESH_TOKEN))
    }

    @Test
    fun untilSomeoneSignsInTheOwnerIsThePlaceholder() = runTest {
        val manager = manager()

        // This is what makes Odo usable before an account exists; adoption moves those rows
        // across on the first sync after sign-in.
        assertFalse(manager.isSignedIn())
        assertEquals(OwnerId.LOCAL_PLACEHOLDER, manager.currentOwnerId())
        assertNull(manager.currentAccessToken())
    }

    @Test
    fun aRelaunchRestoresWithoutTouchingTheNetwork() = runTest {
        val store = InMemoryStore().apply { seed(expiresAt = now.plus(1.hours)) }
        val gateway = RecordingGateway()
        val manager = manager(gateway, store)

        manager.restore()

        assertTrue(manager.isSignedIn())
        assertEquals("stored-access", manager.currentAccessToken())
        assertEquals(0, gateway.calls)
    }

    @Test
    fun aStaleTokenIsRefreshedBeforeItIsHandedOut() = runTest {
        val store = InMemoryStore().apply { seed(expiresAt = now.minus(1.hours)) }
        val gateway = RecordingGateway()
        val manager = manager(gateway, store).also { it.restore() }

        val token = manager.currentAccessToken()

        assertEquals("access-1", token)
        // The refresh token rotates; storing the new one is not optional, or the next
        // relaunch is locked out.
        assertEquals("refresh-1", store.get(SecureStore.KEY_REFRESH_TOKEN))
    }

    @Test
    fun aTokenAboutToExpireIsAlsoRefreshed() = runTest {
        val store = InMemoryStore().apply { seed(expiresAt = now.plus(30.seconds)) }
        val gateway = RecordingGateway()
        val manager = manager(gateway, store).also { it.restore() }

        manager.currentAccessToken()

        assertEquals(1, gateway.calls)
    }

    @Test
    fun aRejectedRefreshEndsTheSessionInsteadOfRetrying() = runTest {
        val store = InMemoryStore().apply { seed(expiresAt = now.minus(1.hours)) }
        val manager = manager(RefusingGateway, store).also { it.restore() }

        val token = manager.currentAccessToken()

        // Terminal: revoked or past renewal. The app keeps working offline and Profile shows
        // the sign-in row again — it does not interrupt whatever the owner was doing.
        assertNull(token)
        assertFalse(manager.isSignedIn())
        assertNull(store.get(SecureStore.KEY_REFRESH_TOKEN))
        assertEquals(OwnerId.LOCAL_PLACEHOLDER, manager.currentOwnerId())
    }

    @Test
    fun aHalfWrittenStoredSessionIsIgnored() = runTest {
        // An access token with no refresh token can only produce confusing failures later.
        val store = InMemoryStore().apply { put(SecureStore.KEY_ACCESS_TOKEN, "orphan") }

        manager(store = store).restore()

        assertFalse(manager(store = store).isSignedIn())
    }

    @Test
    fun signingOutClearsEverythingEvenIfTheServerRefuses() = runTest {
        val store = InMemoryStore()
        val manager = manager(RefusingGateway, store)
        manager.verifyOtpWith(SucceedingGateway(), store)

        manager.signOut()

        // Sign-out has to work on a plane. A refresh token left live on the server is a
        // smaller problem than a device that cannot sign out.
        assertFalse(manager.isSignedIn())
        assertNull(store.get(SecureStore.KEY_REFRESH_TOKEN))
    }

    @Test
    fun requestingACodeStoresNothing() = runTest {
        val store = InMemoryStore()
        val manager = manager(store = store)

        manager.requestOtp(phone)

        assertFalse(manager.isSignedIn())
        assertNull(store.get(SecureStore.KEY_ACCESS_TOKEN))
    }

    /* ------------------------------ scaffolding ------------------------------ */

    private fun manager(
        gateway: AuthGateway = SucceedingGateway(),
        store: SecureStore = InMemoryStore(),
    ) = OdoSessionManager(gateway = gateway, store = store, telemetry = silentTelemetry(), clock = FixedClock(now))

    /** Signs in through a gateway that works, so sign-out has something to clear. */
    private suspend fun OdoSessionManager.verifyOtpWith(gateway: AuthGateway, store: SecureStore) {
        OdoSessionManager(gateway, store, silentTelemetry(), FixedClock(now)).verifyOtp(phone, "123456")
        restore()
    }

    private inner class SucceedingGateway : AuthGateway {
        override suspend fun requestOtp(phone: PhoneNumber) = Unit.right()
        override suspend fun verifyOtp(phone: PhoneNumber, code: String) = issued().right()
        override suspend fun refresh(refreshToken: String) = issued().right()
        override suspend fun signOut(accessToken: String) = Unit.right()
    }

    private inner class RecordingGateway : AuthGateway {
        var calls = 0
        override suspend fun requestOtp(phone: PhoneNumber) = Unit.right()
        override suspend fun verifyOtp(phone: PhoneNumber, code: String) = issued().right()
        override suspend fun refresh(refreshToken: String): Either<DomainError, AuthSession> {
            calls++
            return issued().right()
        }

        override suspend fun signOut(accessToken: String) = Unit.right()
    }

    private object RefusingGateway : AuthGateway {
        override suspend fun requestOtp(phone: PhoneNumber) = DomainError.OtpRequestFailed.left()
        override suspend fun verifyOtp(phone: PhoneNumber, code: String) = DomainError.InvalidOtp.left()
        override suspend fun refresh(refreshToken: String) = DomainError.SessionExpired.left()
        override suspend fun signOut(accessToken: String) = DomainError.SessionExpired.left()
    }

    private fun issued() = AuthSession(
        accessToken = "access-1",
        refreshToken = "refresh-1",
        ownerId = OwnerId("user-1"),
        expiresAt = now.plus(1.hours),
    )

    private fun silentTelemetry() = AuthTelemetry(logger = NoopLogger, analytics = NoopAnalytics)

    private object NoopLogger : Logger {
        override fun log(
            level: LogLevel,
            tag: String,
            event: String,
            traceContext: TraceContext?,
            fields: Map<String, Any?>,
        ) = Unit

        override fun flush() = Unit
    }

    private object NoopAnalytics : AnalyticsTracker {
        override fun identify(traits: UserTraits) = Unit
        override fun track(eventName: String, properties: Map<String, Any?>) = Unit
        override fun setConsent(status: ConsentStatus) = Unit
        override fun flush() = Unit
    }

    private class FixedClock(private val at: Instant) : Clock {
        override fun now(): Instant = at
    }

    private class InMemoryStore : SecureStore {
        private val values = mutableMapOf<String, String>()
        override suspend fun put(key: String, value: String) { values[key] = value }
        override suspend fun get(key: String): String? = values[key]
        override suspend fun remove(key: String) { values.remove(key) }
        override suspend fun clear() = values.clear()

        fun seed(expiresAt: Instant) {
            values[SecureStore.KEY_ACCESS_TOKEN] = "stored-access"
            values[SecureStore.KEY_REFRESH_TOKEN] = "stored-refresh"
            values[SecureStore.KEY_USER_ID] = "user-9"
            values[SecureStore.KEY_EXPIRES_AT] = expiresAt.toString()
        }
    }
}
