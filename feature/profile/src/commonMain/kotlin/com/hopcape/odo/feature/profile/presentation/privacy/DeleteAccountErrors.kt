package com.hopcape.odo.feature.profile.presentation.privacy

import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_da_error_code_expired
import com.hopcape.odo.feature.profile.resources.pf_da_error_code_wrong
import com.hopcape.odo.feature.profile.resources.pf_da_error_failed
import com.hopcape.odo.feature.profile.resources.pf_da_error_local_survived
import com.hopcape.odo.feature.profile.resources.pf_da_error_no_code
import com.hopcape.odo.feature.profile.resources.pf_da_error_reverify
import com.hopcape.odo.feature.profile.resources.pf_da_error_throttled

/**
 * What to tell the owner when an account deletion goes wrong.
 *
 * Kept apart from [toProfileMessage][com.hopcape.odo.feature.profile.presentation.toProfileMessage]
 * because the same errors mean different things here. A generic "couldn't save" is fine for a
 * settings toggle and useless for an irreversible operation the owner is waiting on — every
 * message below says what state their account is actually in, because that is the only thing
 * they need in order to decide what to do next.
 */
internal fun DomainError.toDeleteAccountMessage(): UiText = when (this) {
    DomainError.InvalidOtp -> UiText(Res.string.pf_da_error_code_wrong)
    DomainError.OtpExpired -> UiText(Res.string.pf_da_error_code_expired)
    DomainError.OtpRequestFailed -> UiText(Res.string.pf_da_error_no_code)
    is DomainError.TooManyOtpRequests -> UiText(Res.string.pf_da_error_throttled)
    DomainError.ReVerificationRequired -> UiText(Res.string.pf_da_error_reverify)
    // The account is gone and this phone kept a copy. Says exactly that, because "couldn't
    // delete" would be false in both directions.
    DomainError.LocalDataSurvivedErase -> UiText(Res.string.pf_da_error_local_survived)
    else -> UiText(Res.string.pf_da_error_failed)
}
