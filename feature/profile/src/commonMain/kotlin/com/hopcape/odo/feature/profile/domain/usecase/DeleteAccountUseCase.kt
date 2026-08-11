package com.hopcape.odo.feature.profile.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.auth.AccountEraser
import com.hopcape.odo.core.domain.auth.EraseOutcome
import com.hopcape.odo.core.domain.auth.PhoneVerifier
import com.hopcape.odo.core.domain.auth.VerifiedAccount
import com.hopcape.odo.core.domain.auth.VerifiedPhoneToken
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Erase the owner's account and everything under it, everywhere.
 *
 * **The order is the design.** Each step is harder to undo than the one before it, so they
 * run outermost-first and stop at the first failure:
 *
 * 1. **The server.** If this fails, nothing anywhere has changed and the owner can try
 *    again. Doing the local wipe first would leave someone with an empty app and a live
 *    account, which is the worst of both.
 * 2. **The provider account.** The credential that could sign back in. By now the server
 *    holds nothing, so a failure here is reported honestly rather than swallowed — the
 *    number is still verifiable and the owner needs to know.
 * 3. **This device.** Last, because it is the only step that can be retried on its own.
 * 4. **The session and the verification handle.** Best effort, and not allowed to fail the
 *    deletion: there is nothing left for a stale token to reach.
 *
 * A failure at step 3 is its own error ([DomainError.LocalDataSurvivedErase]) rather than a
 * generic one. It is the single outcome that must not be reported as either success or plain
 * failure — the account is gone and retrying the erase would do nothing, so the only useful
 * offer is to retry the wipe.
 *
 * A device with no account never reaches [erase] at all: [VerifiedAccount.verifiedNumber]
 * answers null, the caller skips straight to [localOnly], and the owner is not made to prove
 * a number to delete data that only ever lived on their phone.
 */
internal class DeleteAccountUseCase(
    private val eraser: AccountEraser,
    private val account: VerifiedAccount,
    private val deleteAllData: DeleteAllDataUseCase,
    private val verifier: PhoneVerifier,
) {

    /**
     * The full erase, given fresh proof of the number.
     *
     * [token] has to be minutes old — the server refuses anything staler. That is why the
     * caller re-runs the OTP rather than reusing the session it already has.
     */
    suspend operator fun invoke(token: VerifiedPhoneToken): Either<DomainError, Unit> {
        val outcome = eraser.erase(token).getOrElse { return it.left() }

        // NO_ACCOUNT is not a failure: the number was proved and the server has nothing under
        // it. The owner asked for their data gone, so the rest of the flow still runs.
        check(outcome == EraseOutcome.DELETED || outcome == EraseOutcome.NO_ACCOUNT)

        account.delete().onLeft { return it.left() }

        return wipeLocally()
    }

    /**
     * The whole deletion for a device that never signed in.
     *
     * There is no account to erase and no credential to remove, so there is nothing to prove
     * and nobody to ask. Making someone verify a number to delete a local database would be
     * ceremony, not safety.
     */
    suspend fun localOnly(): Either<DomainError, Unit> = wipeLocally()

    private suspend fun wipeLocally(): Either<DomainError, Unit> {
        deleteAllData().onLeft {
            // Past the point of no return. Say so precisely rather than reporting a failure
            // that suggests the account survived.
            return DomainError.LocalDataSurvivedErase.left()
        }
        // Best effort, and deliberately after the wipe. Firebase keeps its own signed-in user
        // regardless of Odo's session; leaving one behind would mean the next person opening
        // the app on this phone is already verified as the last one.
        verifier.forget()
        return Unit.right()
    }
}

/** Arrow's `getOrElse` with a non-local return, so the caller reads as a sequence of steps. */
private inline fun <L, R> Either<L, R>.getOrElse(onLeft: (L) -> Nothing): R = when (this) {
    is Either.Left -> onLeft(value)
    is Either.Right -> value
}
