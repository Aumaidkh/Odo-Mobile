package com.hopcape.odo.infrastructure.firebase.auth

import arrow.core.Either
import arrow.core.left
import com.hopcape.odo.core.domain.auth.VerifiedAccount
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * The account port for a target that never signed anyone in.
 *
 * The twin of [UnavailablePhoneVerifier], bound by [firebaseAuthModule] and kept by iOS for
 * the same reason: v1.0 is Android, and a platform that cannot verify a number has no
 * provider account to describe or delete.
 *
 * [delete] refuses rather than succeeding quietly. Reporting success would tell the owner
 * their credential is gone when it never existed — and on a platform that later gains phone
 * auth, a silent no-op here would leave a live sign-in behind after an account deletion.
 * [DomainError.NoVerifiedAccount] is also what the deletion flow reads to skip the whole
 * server round trip, which is the right outcome on a device that only ever held a local
 * profile.
 */
internal class UnavailableVerifiedAccount(
    private val onDiagnostic: (String) -> Unit,
) : VerifiedAccount {

    override suspend fun verifiedNumber(): PhoneNumber? = null

    override suspend fun delete(): Either<DomainError, Unit> {
        onDiagnostic("Phone verification is not available on this platform; no account to delete.")
        return DomainError.NoVerifiedAccount.left()
    }
}
