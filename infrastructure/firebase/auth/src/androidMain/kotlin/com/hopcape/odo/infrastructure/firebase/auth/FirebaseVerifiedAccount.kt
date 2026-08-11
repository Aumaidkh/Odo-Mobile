package com.hopcape.odo.infrastructure.firebase.auth

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.domain.auth.VerifiedAccount
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.tasks.await

/**
 * The Firebase user behind the current sign-in, and the way to remove it.
 *
 * Deleting the provider account **client-side** is deliberate. The server erase has already
 * run by the time this is called and has removed everything Odo holds; what is left is the
 * credential that could sign straight back in. Doing it from here is what keeps a Google
 * service-account key out of the server's secrets — the alternative is giving the Edge
 * Function the power to delete any Firebase user, for the sake of one call the device can
 * make about itself.
 *
 * Android-only for the same reason as [FirebasePhoneVerifier]: v1.0 is an Android release and
 * the iOS side refuses out loud rather than pretending.
 */
internal class FirebaseVerifiedAccount(
    private val auth: FirebaseAuth,
    private val onDiagnostic: (String) -> Unit,
) : VerifiedAccount {

    /**
     * The number Firebase has on file.
     *
     * Read rather than stored, because nothing else in Odo keeps it — the session is a pair
     * of tokens and the profile is what the owner typed. A number Firebase reports in a shape
     * [PhoneNumber] refuses is treated as no number at all: the only use for it is telling
     * the owner where a code is going, and a half-parsed one would be worse than the flow
     * asking them to confirm without it.
     */
    override suspend fun verifiedNumber(): PhoneNumber? =
        auth.currentUser?.phoneNumber?.let { PhoneNumber.of(it).getOrNull() }

    override suspend fun delete(): Either<DomainError, Unit> {
        val user = auth.currentUser ?: return DomainError.NoVerifiedAccount.left()

        return runCatchingCancellableSuspend { user.delete().await() }.fold(
            onSuccess = { Unit.right() },
            onFailure = { error ->
                onDiagnostic("Firebase refused the account deletion: ${error.diagnostic()}")
                when (error) {
                    // Firebase has its own freshness rule, separate from the erase endpoint's
                    // ten minutes, and it can fire even after a verification the server was
                    // happy with. The answer is the same either way: prove the number again.
                    is FirebaseAuthRecentLoginRequiredException -> DomainError.ReVerificationRequired
                    else -> DomainError.AccountEraseFailed(error::class.simpleName)
                }.left()
            },
        )
    }
}
