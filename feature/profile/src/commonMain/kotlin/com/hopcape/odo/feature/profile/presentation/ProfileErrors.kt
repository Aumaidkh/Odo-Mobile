package com.hopcape.odo.feature.profile.presentation

import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.owner.model.OwnerEmail
import com.hopcape.odo.core.domain.owner.model.OwnerName
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_error_email_invalid
import com.hopcape.odo.feature.profile.resources.pf_error_email_too_long
import com.hopcape.odo.feature.profile.resources.pf_error_name_blank
import com.hopcape.odo.feature.profile.resources.pf_error_name_too_long
import com.hopcape.odo.feature.profile.resources.pf_error_name_too_short
import com.hopcape.odo.feature.profile.resources.pf_error_no_profile
import com.hopcape.odo.feature.profile.resources.pf_error_save_failed

/**
 * What to tell the owner about a failure, per domain error.
 *
 * The field errors name the rule they broke, since that is what makes them fixable.
 * Everything else falls back to a plain "couldn't save": an owner cannot act on a
 * persistence failure, and the detail is already on its way to the crash dashboard.
 */
internal fun DomainError.toProfileMessage(): UiText = when (this) {
    DomainError.BlankOwnerName -> UiText(Res.string.pf_error_name_blank)
    is DomainError.OwnerNameTooShort -> UiText(Res.string.pf_error_name_too_short, listOf(OwnerName.MIN_LENGTH))
    is DomainError.OwnerNameTooLong -> UiText(Res.string.pf_error_name_too_long, listOf(OwnerName.MAX_LENGTH))
    DomainError.InvalidOwnerEmail -> UiText(Res.string.pf_error_email_invalid)
    is DomainError.OwnerEmailTooLong -> UiText(Res.string.pf_error_email_too_long, listOf(OwnerEmail.MAX_LENGTH))
    DomainError.ProfileNotFound -> UiText(Res.string.pf_error_no_profile)
    else -> UiText(Res.string.pf_error_save_failed)
}
