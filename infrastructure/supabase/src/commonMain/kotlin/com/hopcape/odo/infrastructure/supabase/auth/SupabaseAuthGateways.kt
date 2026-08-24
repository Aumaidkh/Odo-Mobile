package com.hopcape.odo.infrastructure.supabase.auth

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import com.hopcape.odo.core.domain.auth.AuthGateway
import com.hopcape.odo.core.domain.auth.AuthSession
import com.hopcape.odo.core.domain.auth.OtpRequestOutcome
import com.hopcape.odo.core.domain.auth.PhoneVerificationOutcome
import com.hopcape.odo.core.domain.auth.PhoneVerifier
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Phone sign-in: Firebase proves the number, Supabase issues the session.
 *
 * Two vendors because neither can do both. Supabase relays SMS through Twilio, MessageBird or
 * Vonage, all of which need TRAI DLT registration in India — which is why phone sign-in never
 * worked here. Firebase runs its own registered senders, so it can send the code; but every
 * `owner_id` in this project is a `uuid` referencing `auth.users(id)` and a Firebase UID is a
 * 28-character string, so Firebase's token cannot be the one row-level security checks.
 *
 * This class is only the seam between them. [PhoneVerifier] is a port, so nothing here knows
 * the code came from Firebase; `exchangeFirebaseToken` posts to an Edge Function that trades
 * the proof for a real GoTrue session.
 *
 * [refresh] and [signOut] never touch the verifier: the session is an ordinary Supabase
 * session and renews like one. Sign-out also tells the verifier to forget, because Firebase
 * keeps its own signed-in user and leaving one behind means the next person to open the app
 * on this device is already verified as the last one.
 */
internal class FirebaseBridgeAuthGateway(
    private val verifier: PhoneVerifier,
    private val endpoint: SupabaseTokenEndpoint,
) : AuthGateway {

    override suspend fun requestOtp(phone: PhoneNumber): Either<DomainError, OtpRequestOutcome> =
        verifier.startVerification(phone).flatMap { outcome ->
            when (outcome) {
                PhoneVerificationOutcome.CodeSent -> OtpRequestOutcome.CodeSent.right()
                PhoneVerificationOutcome.AlreadyVerified ->
                    verifier.completeAutoVerification()
                        .flatMap { proof -> endpoint.exchangeFirebaseToken(proof.value) }
                        .map { OtpRequestOutcome.AlreadyVerified(it) }
            }
        }

    /** The number is already held by the verifier from [requestOtp], so it is not passed on. */
    override suspend fun verifyOtp(phone: PhoneNumber, code: String): Either<DomainError, AuthSession> =
        verifier.submitCode(code).flatMap { proof -> endpoint.exchangeFirebaseToken(proof.value) }

    override suspend fun refresh(refreshToken: String): Either<DomainError, AuthSession> =
        endpoint.refresh(refreshToken)

    /**
     * Both sides are told, and the local one is told even if the server call fails — the
     * caller clears local state regardless, so a Firebase user left signed in here would
     * outlive the session that justified it.
     */
    override suspend fun signOut(accessToken: String): Either<DomainError, Unit> {
        val result = endpoint.signOut(accessToken)
        verifier.forget()
        return result
    }
}

/**
 * The development way in: any number, any code, one fixed account.
 *
 * This is not a fake — it produces a **real** Supabase session, with a real JWT, subject to
 * real row-level security. That is the whole point: everything downstream of sign-in (owner
 * stamping, adoption, sync, storage) can be built and verified for real while phone OTP
 * waits on DLT registration.
 *
 * It stands in front of the same screens the real gateway does. `requestOtp` sends nothing
 * and reports success, so the OTP screen appears as usual; `verifyOtp` ignores what was
 * typed and signs in as [DevCredentials]. Swapping to the real gateway is one Koin line and
 * changes nothing above it.
 *
 * Delete this, and [DevCredentials], when phone OTP goes live.
 */
internal class DevPasswordAuthGateway(
    private val endpoint: SupabaseTokenEndpoint,
) : AuthGateway {

    /** No SMS to send. Reported as sent so the flow reaches the code screen. */
    override suspend fun requestOtp(phone: PhoneNumber): Either<DomainError, OtpRequestOutcome> =
        OtpRequestOutcome.CodeSent.right()

    /**
     * Whatever was typed is accepted, and the fixed account is signed in.
     *
     * The code is deliberately not checked. Validating a code nobody sent would only be
     * theatre, and the screens already exercise the wrong-code path against the real gateway.
     */
    override suspend fun verifyOtp(phone: PhoneNumber, code: String): Either<DomainError, AuthSession> =
        endpoint.password(DevCredentials.EMAIL, DevCredentials.PASSWORD)

    override suspend fun refresh(refreshToken: String): Either<DomainError, AuthSession> =
        endpoint.refresh(refreshToken)

    override suspend fun signOut(accessToken: String): Either<DomainError, Unit> =
        endpoint.signOut(accessToken)
}
