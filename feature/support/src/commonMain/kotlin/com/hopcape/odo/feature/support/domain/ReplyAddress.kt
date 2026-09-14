package com.hopcape.odo.feature.support.domain

import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import kotlinx.coroutines.flow.first

/**
 * Where an answer would go, and how much of it to show.
 *
 * Sign-in is a phone number, so an account often carries no address at all. The forms need
 * two different screens for those two cases — one states where the reply goes, the other asks
 * — so this answers with the address and the mask, and lets the screen decide.
 */
internal class ReplyAddress(
    private val profiles: OwnerProfileRepository,
) {

    /** The account's address, or null. Never a blank string, which reads as "there is one". */
    suspend fun current(): String? =
        profiles.observe().first()?.email?.value?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * "r•••@gmail.com" — enough for the owner to recognise, and not enough for a screenshot of
 * the screen to hand it to anyone.
 *
 * The first character and the whole domain survive: those are what say *which* of their
 * addresses this is. Anything that is not an address is masked whole rather than printed,
 * because the only way that happens is data arriving in a shape nobody expected.
 */
internal fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    if (at < 1 || at == email.lastIndex) return MASK
    return "${email.first()}$MASK${email.substring(at)}"
}

private const val MASK = "•••"
