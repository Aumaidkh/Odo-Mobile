package com.hopcape.odo.feature.auth.presentation

import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.domain.auth.AuthGateway
import com.hopcape.odo.core.domain.auth.AuthSession
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.subscription.SubscriptionIdentity
import com.hopcape.odo.core.platform.secure.SecureStore
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.feature.auth.AuthTelemetry
import com.hopcape.odo.feature.auth.domain.OdoSessionManager
import com.hopcape.odo.feature.auth.domain.OtpRequestBroker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * What both sign-in screens need to be built, and nothing either of them is about.
 *
 * Shared because the number screen now hands its request to a broker, so a test of it has to
 * build one — and building one means a session manager, which means every port under it.
 */

internal fun testSessionManager(
    gateway: AuthGateway,
    clock: Clock = FrozenClock,
) = OdoSessionManager(
    gateway = gateway,
    store = InMemorySecureStore(),
    telemetry = silentAuthTelemetry(),
    scheduler = NoopSyncScheduler,
    identity = NoopSubscriptionIdentity,
    profiles = NoopOwnerProfiles,
    clock = clock,
)

internal fun testBroker(gateway: AuthGateway, scope: CoroutineScope) =
    OtpRequestBroker(sessions = testSessionManager(gateway), scope = scope)

internal fun silentAuthTelemetry() = AuthTelemetry(SilentAuthLogger, SilentAuthAnalytics)

internal fun testSession() = AuthSession(
    accessToken = "access-1",
    refreshToken = "refresh-1",
    ownerId = OwnerId("user-1"),
    expiresAt = Instant.parse("2026-08-03T11:00:00Z"),
)

internal object FrozenClock : Clock {
    override fun now(): Instant = Instant.parse("2026-08-03T10:00:00Z")
}

internal class InMemorySecureStore : SecureStore {
    private val values = mutableMapOf<String, String>()
    override suspend fun put(key: String, value: String) { values[key] = value }
    override suspend fun get(key: String): String? = values[key]
    override suspend fun remove(key: String) { values.remove(key) }
    override suspend fun clear() = values.clear()
}

/** These tests are about the screens, not about scheduling or where a number is stored. */
internal object NoopSyncScheduler : SyncScheduler {
    override fun scheduleStartupSync() = Unit
    override fun requestSync(reason: SyncReason) = Unit
}

internal object NoopOwnerProfiles : OwnerProfileRepository {
    override suspend fun save(profile: OwnerProfile) = profile.right()
    override fun observe(): Flow<OwnerProfile?> = flowOf(null)
    override suspend fun recordPhone(ownerId: OwnerId, phone: PhoneNumber) = Unit.right()
    override suspend fun delete() = Unit.right()
}

internal object NoopSubscriptionIdentity : SubscriptionIdentity {
    override fun identify(ownerId: OwnerId) = Unit
    override fun forget() = Unit
}

internal object SilentAuthLogger : Logger {
    override fun log(
        level: LogLevel,
        tag: String,
        event: String,
        traceContext: TraceContext?,
        fields: Map<String, Any?>,
    ) = Unit

    override fun flush() = Unit
}

internal object SilentAuthAnalytics : AnalyticsTracker {
    override fun identify(traits: UserTraits) = Unit
    override fun track(eventName: String, properties: Map<String, Any?>) = Unit
    override fun setConsent(status: ConsentStatus) = Unit
    override fun flush() = Unit
}

/** Answers nothing: used where a test must prove a request was never made. */
internal object NeverAskedGateway : AuthGateway {
    override suspend fun requestOtp(phone: PhoneNumber) =
        error("no request should reach the gateway in this test")

    override suspend fun verifyOtp(phone: PhoneNumber, code: String) =
        error("no verification should reach the gateway in this test")

    override suspend fun refresh(refreshToken: String) = testSession().right()
    override suspend fun signOut(accessToken: String) = Unit.right()
}
