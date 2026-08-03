package com.hopcape.odo.infrastructure.supabase.auth

import arrow.core.Either
import arrow.core.right
import com.hopcape.odo.core.domain.auth.AuthGateway
import com.hopcape.odo.core.domain.auth.AuthSession
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Phone OTP through Supabase — the real one.
 *
 * **Needs a configured SMS provider to work at all.** Supabase relays through Twilio,
 * MessageBird or Vonage, and in India that also means TRAI DLT registration for the sender
 * id and message template (TDD Open Q#2, unresolved). Until that is in place `requestOtp`
 * comes back as [DomainError.OtpRequestFailed] and no code is ever sent — which is why
 * [DevPasswordAuthGateway] exists alongside it.
 */
internal class SupabaseOtpAuthGateway(
    private val endpoint: SupabaseTokenEndpoint,
) : AuthGateway {

    override suspend fun requestOtp(phone: PhoneNumber): Either<DomainError, Unit> =
        endpoint.sendOtp(phone.value)

    override suspend fun verifyOtp(phone: PhoneNumber, code: String): Either<DomainError, AuthSession> =
        endpoint.verifyOtp(phone.value, code)

    override suspend fun refresh(refreshToken: String): Either<DomainError, AuthSession> =
        endpoint.refresh(refreshToken)

    override suspend fun signOut(accessToken: String): Either<DomainError, Unit> =
        endpoint.signOut(accessToken)
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

    /** No SMS to send. Reported as success so the flow reaches the code screen. */
    override suspend fun requestOtp(phone: PhoneNumber): Either<DomainError, Unit> = Unit.right()

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
