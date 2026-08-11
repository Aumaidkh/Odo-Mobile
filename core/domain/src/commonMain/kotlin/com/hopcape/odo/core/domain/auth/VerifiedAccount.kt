package com.hopcape.odo.core.domain.auth

import arrow.core.Either
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * The account the SMS provider holds for whoever is signed in on this device.
 *
 * Separate from [PhoneVerifier] because the two answer different questions. That port proves
 * a number in the middle of a sign-in; this one describes and removes the account left behind
 * afterwards. Nothing in the deletion flow needs to start a verification, and nothing in the
 * sign-in flow needs to delete anything, so neither has to carry the other's methods.
 *
 * The implementation is the same vendor either way (Firebase today), which is exactly why the
 * split has to be made here rather than left to the adapter.
 */
interface VerifiedAccount {

    /**
     * The number the provider has on file, or null when nobody is signed in with one.
     *
     * Read rather than stored because nothing else in Odo keeps it: neither [AuthSession] nor
     * [OwnerProfile][com.hopcape.odo.core.domain.owner.model.OwnerProfile] holds a phone
     * number, by design — the session is a pair of tokens and the profile is what the owner
     * typed. The deletion flow needs it only to say which number a code is going to.
     */
    suspend fun verifiedNumber(): PhoneNumber?

    /**
     * Delete the provider's own account for this device.
     *
     * Deliberately client-side. The server erase runs first and removes everything Odo holds;
     * this removes the credential that could sign back in. Doing it from here is what keeps a
     * Google service-account key out of the server's secrets.
     *
     * Fails with [DomainError.ReVerificationRequired] when the provider considers the local
     * sign-in too old to authorise a deletion, and with [DomainError.NoVerifiedAccount] when
     * there is nothing signed in to delete.
     */
    suspend fun delete(): Either<DomainError, Unit>
}
