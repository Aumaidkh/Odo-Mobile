package com.hopcape.odo.core.data.auth

import arrow.core.Either
import arrow.core.right
import com.hopcape.odo.core.domain.auth.AccountEraser
import com.hopcape.odo.core.domain.auth.EraseOutcome
import com.hopcape.odo.core.domain.auth.VerifiedPhoneToken
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * The eraser for a build with no backend.
 *
 * Answers [EraseOutcome.NO_ACCOUNT], and that is the truthful answer rather than a
 * placeholder: a build with no Supabase credentials has never pushed a row anywhere, so there
 * is genuinely no server account to erase. The deletion flow reads it as "nothing to do here"
 * and carries on to the local wipe, which is the whole of what deletion means on such a
 * device.
 *
 * The alternative — failing — would leave a developer checkout unable to exercise the
 * deletion flow at all, and would tell the owner of an offline build that something went
 * wrong when nothing did.
 *
 * `supabaseModule` replaces this with the real adapter the moment the build has credentials,
 * the same swap every remote data source in this module gets.
 */
internal class OfflineAccountEraser : AccountEraser {

    override suspend fun erase(token: VerifiedPhoneToken): Either<DomainError, EraseOutcome> =
        EraseOutcome.NO_ACCOUNT.right()
}
