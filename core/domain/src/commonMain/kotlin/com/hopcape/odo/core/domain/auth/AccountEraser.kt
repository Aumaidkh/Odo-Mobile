package com.hopcape.odo.core.domain.auth

import arrow.core.Either
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Erases everything the server holds for an owner, given fresh proof of their number.
 *
 * A port rather than a repository method because the erase is not a write to any one table:
 * it removes stored files, then the rows that reference them, then the account itself, and
 * it runs somewhere that holds a key the app never sees.
 *
 * Takes a [VerifiedPhoneToken] rather than the session's access token on purpose. An
 * ordinary session lasts an hour and proves only that somebody signed in at some point; this
 * is irreversible, so the server requires proof minted minutes ago and rejects anything
 * older. That constraint is the reason the deletion flow re-runs the OTP at all.
 */
interface AccountEraser {

    /** Erase the account behind [token], or say why it could not be done. */
    suspend fun erase(token: VerifiedPhoneToken): Either<DomainError, EraseOutcome>
}

/** What the server did when asked to erase an account. */
enum class EraseOutcome {

    /** The account and everything under it is gone. */
    DELETED,

    /**
     * There was no account to erase — the number was proved, but no owner row was ever
     * created for it.
     *
     * Not a failure. The owner asked for their data gone and the server has none, so the
     * flow carries on to the local wipe rather than stopping to report a problem that only
     * exists from the server's point of view.
     */
    NO_ACCOUNT,
}
