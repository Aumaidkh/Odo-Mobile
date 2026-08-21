package com.hopcape.odo.infrastructure.firebase.auth

import arrow.core.Either
import arrow.core.left
import com.hopcape.odo.core.domain.auth.PhoneVerificationOutcome
import com.hopcape.odo.core.domain.auth.PhoneVerifier
import com.hopcape.odo.core.domain.auth.VerifiedPhoneToken
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * The verifier for a target that cannot send an SMS.
 *
 * This is what [firebaseAuthModule] binds, and iOS keeps it: v1.0 is Android, and Firebase
 * phone auth on iOS needs an APNs auth key, silent-push handling in the AppDelegate and a
 * reCAPTCHA fallback, none of which exists here. Android replaces it from
 * `firebaseAuthAndroidModule`, which the app bootstrap includes in its platform module.
 *
 * It reports [DomainError.OtpRequestFailed] rather than pretending to succeed. Reporting
 * success would send the owner to a code screen for a message that is never coming, and the
 * only signal would be that nothing ever arrives.
 *
 * It is also the honest answer if a bootstrap ever forgets the Android module: sign-in
 * refuses out loud, with a line in the log saying why, instead of half-working.
 */
internal class UnavailablePhoneVerifier(
    private val onDiagnostic: (String) -> Unit,
) : PhoneVerifier {

    override suspend fun startVerification(phone: PhoneNumber): Either<DomainError, PhoneVerificationOutcome> {
        onDiagnostic("Phone verification is not available on this platform; no code was sent.")
        return DomainError.OtpRequestFailed.left()
    }

    override suspend fun submitCode(code: String): Either<DomainError, VerifiedPhoneToken> =
        DomainError.OtpExpired.left()

    override suspend fun completeAutoVerification(): Either<DomainError, VerifiedPhoneToken> =
        DomainError.OtpExpired.left()

    /** Nothing was ever started, so there is nothing to drop. */
    override suspend fun forget() = Unit
}
