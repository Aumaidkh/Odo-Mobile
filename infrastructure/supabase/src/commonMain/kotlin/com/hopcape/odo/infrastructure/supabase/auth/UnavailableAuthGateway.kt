package com.hopcape.odo.infrastructure.supabase.auth

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.auth.AuthGateway
import com.hopcape.odo.core.domain.auth.AuthSession
import com.hopcape.odo.core.domain.auth.OtpRequestOutcome
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.infrastructure.supabase.observability.SupabaseTelemetry

/**
 * The gateway for a build with no Supabase credentials.
 *
 * Every other port in `supabaseModule` may go unbound when the build is unconfigured, because
 * `coreDataModule` has already bound an offline fake underneath it — the override simply does
 * not happen and the fake stands. Nothing binds an `AuthGateway` underneath, so the same
 * shape left a hole, and `LateBoundAuthGateway` looks the gateway up *per call*: the hole
 * stayed invisible until an owner tapped "Send code", and then killed the app with
 * `NoDefinitionFoundException` (1.3.3, Crashlytics 893bc4b1).
 *
 * This is the fake that was missing. It refuses out loud rather than pretending to succeed —
 * reporting success would send the owner to a code screen for a message that is never coming.
 *
 * An unconfigured build is a normal state, not a broken one: Odo works fully offline and the
 * only thing that stops working is signing in. Everything the owner has stays on the device.
 */
internal class UnavailableAuthGateway(
    private val telemetry: SupabaseTelemetry,
) : AuthGateway {

    override suspend fun requestOtp(phone: PhoneNumber): Either<DomainError, OtpRequestOutcome> {
        telemetry.signInUnavailable()
        return DomainError.OtpRequestFailed.left()
    }

    /**
     * Unreachable through the screens — [requestOtp] fails first, so no code screen is ever
     * shown. Answered rather than thrown because a restored process could still land here.
     */
    override suspend fun verifyOtp(phone: PhoneNumber, code: String): Either<DomainError, AuthSession> =
        DomainError.OtpRequestFailed.left()

    /**
     * A build that cannot sign in cannot be holding a session worth refreshing. Failing is
     * what makes the session manager drop what it has instead of retrying forever.
     */
    override suspend fun refresh(refreshToken: String): Either<DomainError, AuthSession> =
        DomainError.OtpRequestFailed.left()

    /**
     * Success, and not as a shortcut: there is no server session to end, and the caller
     * clears the local one either way. Failing here would only leave the owner unable to
     * sign out of a session that exists nowhere.
     */
    override suspend fun signOut(accessToken: String): Either<DomainError, Unit> = Unit.right()
}
