package com.hopcape.odo.feature.auth.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.auth.AccessTokenProvider
import com.hopcape.odo.core.domain.auth.AuthGateway
import com.hopcape.odo.core.domain.auth.AuthSession
import com.hopcape.odo.core.domain.auth.OtpRequestOutcome
import com.hopcape.odo.core.domain.auth.SessionRestore
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.SessionStatusProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.subscription.SubscriptionIdentity
import com.hopcape.odo.core.platform.secure.SecureStore
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.feature.auth.AuthTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The one place that knows whether this install has a session, and the only thing allowed
 * to change that.
 *
 * Lives in `:feature:auth` rather than next to the auth provider because none of what it
 * does is vendor-specific: holding a session, deciding when it is stale, persisting it, and
 * clearing it are auth's *policy*. Speaking HTTP to an identity service is the adapter's
 * job, and that sits behind [AuthGateway].
 *
 * That split is also what keeps the dependency arrows straight. `:infrastructure:supabase`
 * needs the current token for every request; it gets it through [AccessTokenProvider] in
 * `:core:domain`, so infrastructure never depends on a feature.
 *
 * **Everything is serialised behind one mutex.** A sync pass, a foreground trigger and a
 * screen can all reach for a token at once, and two concurrent refreshes would race —
 * refresh tokens rotate, so the slower of the two would return one the server has already
 * invalidated.
 */
internal class OdoSessionManager(
    private val gateway: AuthGateway,
    private val store: SecureStore,
    private val telemetry: AuthTelemetry,
    private val scheduler: SyncScheduler,
    private val identity: SubscriptionIdentity,
    private val profiles: OwnerProfileRepository,
    private val clock: Clock = Clock.System,
) : SessionStatusProvider, CurrentOwnerProvider, AccessTokenProvider, SessionRestore {

    private val mutex = Mutex()
    private val _session = MutableStateFlow<AuthSession?>(null)

    /** The current session, for anything that needs to observe sign-in state. */
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    /* ---- SessionRestore ---- */

    /**
     * Read the stored session into memory. Called once at startup, before anything asks.
     *
     * Restoring is not the same as validating: a session read from disk may already be past
     * its expiry, and the first call that needs a token refreshes it.
     */
    override suspend fun restore() {
        mutex.withLock { restoreLocked() }
        // The restored session carries no number of its own — it is four strings read back
        // out of the store — so the one kept beside it is put on the profile again here.
        // Without this, an install that signed in before this release ships would never get
        // its number onto the server: nothing else calls the sign-in path again.
        recordSessionPhone()
    }

    /* ---- SessionStatusProvider ---- */

    /**
     * Whether there is a session at all — not whether its token is fresh.
     *
     * Synchronous by contract, so it reads the cached value rather than reaching for the
     * network. A screen asking a question must never be what triggers a round trip.
     */
    override fun isSignedIn(): Boolean = _session.value != null

    /* ---- CurrentOwnerProvider ---- */

    /**
     * Who owns rows written right now: the signed-in user, or the offline placeholder.
     *
     * The placeholder is what makes Odo usable before anyone signs in. Rows stamped with it
     * are moved onto the real account by the adoption step on the first sync after sign-in
     * (SYNC_DESIGN §9).
     */
    override fun currentOwnerId(): OwnerId = _session.value?.ownerId ?: OwnerId.LOCAL_PLACEHOLDER

    /* ---- AccessTokenProvider ---- */

    /**
     * A token good to send right now, refreshing first if it is stale.
     *
     * Null rather than an error when there is no session: the callers are the HTTP layer and
     * the sync gate, and for both "not signed in" means stay offline, not fail.
     */
    override suspend fun currentAccessToken(): String? = mutex.withLock {
        // Reads the store when memory is empty rather than trusting that startup got there
        // first. The sync gate asks from a background worker that can be running before the
        // startup restore completes, and a null answer there is not retried — it skips the
        // whole run. Costs one read on a cold process and nothing afterwards.
        val current = _session.value ?: restoreLocked() ?: return@withLock null
        if (!current.needsRefresh(clock.now())) return@withLock current.accessToken

        refreshLocked(current)?.accessToken
    }

    /* ---- sign in / out ---- */

    /**
     * Ask for a code. Nothing is stored until one is verified — unless the provider proves
     * the number without ever sending one, in which case sign-in is already done and this
     * keeps the session exactly as [verifyOtp] would.
     */
    suspend fun requestOtp(phone: PhoneNumber): Either<DomainError, OtpRequestOutcome> =
        gateway.requestOtp(phone)
            .onRight { outcome ->
                when (outcome) {
                    OtpRequestOutcome.CodeSent -> telemetry.otpRequested()
                    is OtpRequestOutcome.AlreadyVerified -> completeSignIn(outcome.session, phone)
                }
            }
            .onLeft { telemetry.otpRejected(it) }

    /** Exchange a code for a session and keep it. */
    suspend fun verifyOtp(phone: PhoneNumber, code: String): Either<DomainError, AuthSession> =
        gateway.verifyOtp(phone, code)
            .onRight { completeSignIn(it, phone) }
            .onLeft { telemetry.otpRejected(it) }

    /** The one place a sign-in actually completes, whether a code was typed or not. */
    private suspend fun completeSignIn(session: AuthSession, phone: PhoneNumber) {
        mutex.withLock { keep(session) }
        // Every sign-in, not only the first. The server fills `profiles.phone` from a trigger
        // that runs when the account is created, so an account whose row was written before
        // the number was set keeps NULL forever and nothing on the server ever revisits it.
        // Writing it here, before the sync below, is what gets the number onto the row the
        // push is about to carry.
        store.put(SecureStore.KEY_PHONE, phone.value)
        profiles.recordPhone(session.ownerId, phone)
        telemetry.signedIn()
        // Back up what is already on the phone now. Nothing else will until the owner edits
        // something or relaunches, and they were just told this is what signing in does.
        // `SignIn` is REPLACE, so it also clears a backoff earned by runs that failed for the
        // very reason this just fixed.
        scheduler.requestSync(SyncReason.SignIn)
    }

    /**
     * Put the stored number back on the profile row, if there is a session and a number.
     *
     * Cheap to repeat: the statement only touches the row when the value differs, so a
     * relaunch does not mark a correct row dirty and ask the server to accept a change that
     * isn't one. A failure is ignored — the number is not what a launch is for, and the next
     * one tries again.
     */
    private suspend fun recordSessionPhone() {
        val owner = _session.value?.ownerId ?: return
        val phone = store.get(SecureStore.KEY_PHONE)?.let { PhoneNumber.of(it).getOrNull() } ?: return
        profiles.recordPhone(owner, phone)
    }

    /**
     * Forget the session, here and on the server.
     *
     * The server call is best effort and its result is ignored: sign-out has to work on a
     * plane, and a refresh token left live is a smaller problem than a device that cannot
     * sign out. Local state is cleared either way.
     *
     * This does **not** wipe the owner's rows — that is the sign-out use case's job, because
     * it is a product decision rather than a session one.
     */
    suspend fun signOut() = mutex.withLock {
        _session.value?.let { gateway.signOut(it.accessToken) }
        hold(null)
        SESSION_KEYS.forEach { store.remove(it) }
        telemetry.signedOut()
    }

    /**
     * Renew, or say we could not — which is not the same as giving up.
     *
     * A **rejected** refresh token is terminal: revoked, or expired past renewal. Nothing
     * changes that, so the session is cleared, the app keeps working offline, and Profile
     * shows the sign-in row again.
     *
     * Anything else leaves the session exactly where it is and answers null for now. A
     * timeout, a dropped connection, a 5xx from the identity service — none of those are
     * evidence about the token, and treating them as such ended sessions on a flaky
     * connection. That was silent by design (nothing interrupts the owner), so an install
     * simply stopped syncing, and the next sign-in resumed from cursors nothing had cleared
     * (issue #312). The caller sees null and stays offline for this attempt; the next one
     * tries the same refresh token again, which is still good.
     *
     * Caller already holds the mutex.
     */
    private suspend fun refreshLocked(current: AuthSession): AuthSession? =
        gateway.refresh(current.refreshToken).fold(
            ifLeft = { error ->
                if (error == DomainError.SessionExpired) {
                    hold(null)
                    SESSION_KEYS.forEach { key -> store.remove(key) }
                    // Nothing interrupts the owner, so this line is the only trace that an
                    // install has quietly stopped syncing.
                    telemetry.sessionEnded()
                }
                null
            },
            ifRight = { renewed -> keep(renewed) },
        )

    /** Hold it in memory and on disk, and hand it back. */
    private suspend fun keep(session: AuthSession): AuthSession {
        hold(session)
        store.put(SecureStore.KEY_ACCESS_TOKEN, session.accessToken)
        store.put(SecureStore.KEY_REFRESH_TOKEN, session.refreshToken)
        store.put(SecureStore.KEY_EXPIRES_AT, session.expiresAt.toString())
        store.put(SecureStore.KEY_USER_ID, session.ownerId.value)
        return session
    }

    /** Load from the store into memory, reporting it once. Caller already holds the mutex. */
    private suspend fun restoreLocked(): AuthSession? =
        readFromStore()?.also { telemetry.sessionRestored() }

    /**
     * What the secure store holds, or null if any part is missing.
     *
     * All-or-nothing: a half-written session — an access token with no refresh token — can
     * only produce confusing failures later, and signing in again is cheap.
     */
    private suspend fun readFromStore(): AuthSession? {
        val access = store.get(SecureStore.KEY_ACCESS_TOKEN) ?: return null
        val refresh = store.get(SecureStore.KEY_REFRESH_TOKEN) ?: return null
        val owner = store.get(SecureStore.KEY_USER_ID) ?: return null
        val expiresAt = store.get(SecureStore.KEY_EXPIRES_AT)
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: return null

        return AuthSession(access, refresh, OwnerId(owner), expiresAt).also { hold(it) }
    }

    /**
     * The one place the session changes, so the store hears about it every time.
     *
     * Telling the payments vendor who this device is belongs here rather than at each of the
     * five call sites that used to assign the field: sign-in, a restored session, a renewed
     * one, sign-out and a refresh that was refused all have to reach it, and one of them
     * being forgotten is exactly how a paid owner ends up on the free plan.
     *
     * Neither identity call blocks or can fail — that is [SubscriptionIdentity]'s contract —
     * so this stays inside the mutex without holding a network call under it.
     */
    private fun hold(session: AuthSession?) {
        _session.value = session
        if (session != null) identity.identify(session.ownerId) else identity.forget()
    }

    private companion object {
        val SESSION_KEYS = listOf(
            SecureStore.KEY_ACCESS_TOKEN,
            SecureStore.KEY_REFRESH_TOKEN,
            SecureStore.KEY_EXPIRES_AT,
            SecureStore.KEY_USER_ID,
            SecureStore.KEY_PHONE,
        )
    }
}
