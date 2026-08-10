package com.hopcape.odo.infrastructure.firebase.auth

import arrow.core.Either
import arrow.core.left
import com.hopcape.odo.core.domain.auth.PhoneVerifier
import com.hopcape.odo.core.domain.auth.VerifiedPhoneToken
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Platform factory for the [PhoneVerifier] this module publishes.
 *
 * A genuine expect/actual rather than one gitlive-backed commonMain implementation, for two
 * reasons: gitlive's firebase-auth does not surface `PhoneAuthProvider` in common code, and
 * Android's phone verification is bound to an `Activity` for Play Integrity and the reCAPTCHA
 * fallback — there is no target-neutral shape to write. Same situation as
 * `:infrastructure:firebase:performance`.
 *
 * [onDiagnostic] is how an SDK failure becomes visible. Every Firebase adapter in this repo
 * holds the same contract: a vendor failure is reported and mapped to a [DomainError], never
 * thrown and never silently dropped.
 */
internal expect fun createPhoneVerifier(onDiagnostic: (String) -> Unit): PhoneVerifier

/**
 * The verifier for a target that cannot send an SMS.
 *
 * iOS uses this permanently — v1.0 is Android, and Firebase phone auth on iOS needs an APNs
 * auth key, silent-push handling in the AppDelegate and a reCAPTCHA fallback, none of which
 * exists here.
 *
 * It reports [DomainError.OtpRequestFailed] rather than pretending to succeed. Reporting
 * success would send the owner to a code screen for a message that is never coming, and the
 * only signal would be that nothing ever arrives.
 */
internal class UnavailablePhoneVerifier(
    private val onDiagnostic: (String) -> Unit,
) : PhoneVerifier {

    override suspend fun startVerification(phone: PhoneNumber): Either<DomainError, Unit> {
        onDiagnostic("Phone verification is not available on this platform; no code was sent.")
        return DomainError.OtpRequestFailed.left()
    }

    override suspend fun submitCode(code: String): Either<DomainError, VerifiedPhoneToken> =
        DomainError.OtpExpired.left()

    /** Nothing was ever started, so there is nothing to drop. */
    override suspend fun forget() = Unit
}
