package com.hopcape.odo.core.domain.auth

import arrow.core.Either
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Whoever issues sessions — the one thing in auth that talks to a server.
 *
 * A port, so `:feature:auth` owns *when* to sign in, refresh or sign out while knowing
 * nothing about who does it. That split is what lets phone OTP and a development
 * email/password account be the same feature: two implementations, one Koin line apart.
 *
 * It matters right now rather than as future-proofing. Phone OTP in India needs TRAI DLT
 * registration and a paid SMS provider (TDD Open Q#2, unresolved), and none of the flow
 * above this port can be built or tested while that is pending.
 *
 * **Deliberately not a session store.** This mints and renews sessions; keeping one, deciding
 * when it is stale and handing out the current token belong to the feature, because they are
 * policy rather than protocol.
 */
interface AuthGateway {

    /**
     * Ask for a code to be sent to [phone].
     *
     * Answers as soon as the provider accepts the request — not when the SMS arrives, which
     * nothing on the device can observe. A number that is well-formed but unreachable looks
     * identical to one that works, and the only signal is that no code ever turns up.
     *
     * Can also finish sign-in outright: some providers prove a number without ever sending a
     * code, and [OtpRequestOutcome.AlreadyVerified] carries the session that produced rather
     * than making the caller wait on [verifyOtp] for a code that was never sent.
     */
    suspend fun requestOtp(phone: PhoneNumber): Either<DomainError, OtpRequestOutcome>

    /**
     * Exchange a code for a session.
     *
     * [DomainError.InvalidOtp] and [DomainError.OtpExpired] are kept apart because the answer
     * differs: retype, or resend.
     */
    suspend fun verifyOtp(phone: PhoneNumber, code: String): Either<DomainError, AuthSession>

    /**
     * Renew a session from its refresh token.
     *
     * The token **rotates**: the session that comes back carries a new one and the old one
     * stops working. Storing what comes back is not optional — dropping it locks the install
     * out until someone signs in again.
     */
    suspend fun refresh(refreshToken: String): Either<DomainError, AuthSession>

    /**
     * Tell the server this session is finished.
     *
     * Best effort by design. Sign-out has to work on a plane, so the caller clears local
     * state whatever this answers — a refresh token left live on the server is a smaller
     * problem than a device that cannot sign out.
     */
    suspend fun signOut(accessToken: String): Either<DomainError, Unit>
}

/**
 * What asking for a code turned into.
 *
 * Split from a bare success because a provider can verify a number without ever sending a
 * code ([PhoneVerificationOutcome.AlreadyVerified] one layer down) — the caller needs to know
 * sign-in is already done rather than navigating to a screen that waits for a code that is not
 * coming.
 */
sealed interface OtpRequestOutcome {
    /** A code is on its way; the caller collects it and calls [AuthGateway.verifyOtp]. */
    data object CodeSent : OtpRequestOutcome

    /** Already signed in — no code was ever sent. */
    data class AlreadyVerified(val session: AuthSession) : OtpRequestOutcome
}
