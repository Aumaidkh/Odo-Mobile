package com.hopcape.odo.feature.auth.domain

import arrow.core.Either
import com.hopcape.odo.core.domain.auth.AuthGateway
import com.hopcape.odo.core.domain.auth.AuthSession
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * An [AuthGateway] that looks up the real one on every call instead of holding it.
 *
 * [OdoSessionManager] is a `single` that the startup path resolves before the first frame,
 * so whatever gateway was bound at that moment is the one it keeps for the life of the
 * process. That makes the binding's *build order* decide which way in the app uses, which is
 * not something the session manager should be sensitive to — which gateway is in play is a
 * configuration question (`supabase.phoneAuth`, and whether the build has credentials at
 * all), and answering it per call costs a map lookup.
 *
 * It is also what lets an instrumented test put a fake in front of the sign-in screens
 * without restarting the process.
 */
internal class LateBoundAuthGateway(
    private val gateway: () -> AuthGateway,
) : AuthGateway {

    override suspend fun requestOtp(phone: PhoneNumber): Either<DomainError, Unit> =
        gateway().requestOtp(phone)

    override suspend fun verifyOtp(phone: PhoneNumber, code: String): Either<DomainError, AuthSession> =
        gateway().verifyOtp(phone, code)

    override suspend fun refresh(refreshToken: String): Either<DomainError, AuthSession> =
        gateway().refresh(refreshToken)

    override suspend fun signOut(accessToken: String): Either<DomainError, Unit> =
        gateway().signOut(accessToken)
}
