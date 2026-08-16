package com.hopcape.odo.feature.auth.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.auth.AuthGateway
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.core.domain.auth.AuthSession
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.subscription.SubscriptionIdentity
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

    @Test
    fun aTokenIsReadableWithoutAnyoneHavingCalledRestore() = runTest {
        val store = InMemoryStore()
        manager().verifyOtpWith(SucceedingGateway(), store)

        // A fresh process, nothing restored yet — which is what the sync gate meets when its
        // worker runs before startup finishes. A null here skips the whole run and nothing
        // retries it, so the store has to be consulted on demand rather than only at launch.
        val cold = manager(store = store)

        assertEquals("access-1", cold.currentAccessToken())
        assertTrue(cold.isSignedIn())
    }

    @Test
    fun signingInAsksForASync() = runTest {
        val requested = mutableListOf<SyncReason>()
        val manager = OdoSessionManager(
            gateway = SucceedingGateway(),
            store = InMemoryStore(),
            telemetry = silentTelemetry(),
            scheduler = RecordingScheduler(requested),
            identity = RecordingIdentity(),
            clock = FixedClock(now),
        )

        manager.verifyOtp(phone, "123456")

        // Signing in is what the owner was told backs their data up. Without this the
        // startup pass has already been and gone, and nothing is sent until they edit
        // something or relaunch. The reason matters as much as the call: SignIn is the one
        // that replaces a pending job instead of queueing behind its backoff.
        assertEquals(listOf(SyncReason.SignIn), requested)
    }

    @Test
    fun aFailedSignInAsksForNothing() = runTest {
        val requested = mutableListOf<SyncReason>()
        val manager = OdoSessionManager(
            gateway = RefusingGateway,
            store = InMemoryStore(),
            telemetry = silentTelemetry(),
            scheduler = RecordingScheduler(requested),
            identity = RecordingIdentity(),
            clock = FixedClock(now),
        )

        manager.verifyOtp(phone, "000000")

        assertTrue(requested.isEmpty())
    }

    /* ------------------------------ the store's idea of who this is ------------------------------ */

    @Test
    fun signingInTellsTheStoreWhoThisIs() = runTest {
        // Without this a subscription bought before signing in stays on an anonymous
        // identity, and a new phone signed into the same number does not get Pro back.
        val identity = RecordingIdentity()

        manager(identity = identity).verifyOtp(phone, "123456")

        assertEquals(listOf(issued().ownerId.value), identity.identified)
    }

    @Test
    fun signingOutReturnsTheStoreToAnAnonymousIdentity() = runTest {
        val identity = RecordingIdentity()
        val manager = manager(identity = identity)
        manager.verifyOtp(phone, "123456")

        manager.signOut()

        assertEquals(1, identity.forgotten)
    }

    @Test
    fun aRestoredSessionIsIdentifiedToo() = runTest {
        // A relaunch never goes through verifyOtp, so without this the store would only ever
        // hear about an owner on the launch they signed in.
        val store = InMemoryStore()
        val identity = RecordingIdentity()
        manager(store = store).verifyOtp(phone, "123456")

        manager(store = store, identity = identity).restore()

        assertEquals(listOf(issued().ownerId.value), identity.identified)
    }

    /* ------------------------------ scaffolding ------------------------------ */

    /** Most tests do not care that a sync was asked for. */
    private val trigger = RecordingScheduler(mutableListOf())

    /** Records who the store was told about, so the link and the clear can be asserted. */
    private class RecordingIdentity : SubscriptionIdentity {
        val identified = mutableListOf<String>()
        var forgotten = 0
            private set

        override fun identify(ownerId: OwnerId) { identified += ownerId.value }
        override fun forget() { forgotten++ }
    }

    /** Records what was asked of the scheduler, without WorkManager anywhere near it. */
    private class RecordingScheduler(private val into: MutableList<SyncReason>) : SyncScheduler {
        override fun scheduleStartupSync() = Unit
        override fun requestSync(reason: SyncReason) { into += reason }
    }

    private fun manager(
        gateway: AuthGateway = SucceedingGateway(),
        store: SecureStore = InMemoryStore(),
        identity: SubscriptionIdentity = RecordingIdentity(),
    ) = OdoSessionManager(
        gateway = gateway,
        store = store,
        telemetry = silentTelemetry(),
        scheduler = trigger,
        identity = identity,
        clock = FixedClock(now),
    )

    /** Signs in through a gateway that works, so sign-out has something to clear. */
    private suspend fun OdoSessionManager.verifyOtpWith(gateway: AuthGateway, store: SecureStore) {
        OdoSessionManager(gateway, store, silentTelemetry(), trigger, RecordingIdentity(), FixedClock(now))
            .verifyOtp(phone, "123456")
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
