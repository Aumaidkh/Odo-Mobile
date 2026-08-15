package com.hopcape.odo.feature.refuel

import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.feature.refuel.domain.DraftPayload
import com.hopcape.odo.feature.refuel.domain.toInput

/**
 * Where a detected fill goes when the owner taps Edit on its notification.
 *
 * The notification carries the draft as an opaque string, because it has to survive the
 * process being killed between the payment and the tap. This turns that string back into a
 * destination, and it is the only thing outside this feature that needs to know the string
 * means anything at all — the platform layer that posts the notification treats it as bytes.
 *
 * Public because the app's activity is what receives the intent. Everything it uses to do the
 * job — the encoding, the draft, the mapping — stays internal.
 *
 * `null` for anything that will not decode: an intent from an older build, or a truncated
 * extra. The owner lands on wherever the app normally opens rather than on a crash, and the
 * fill is still waiting for them.
 */
object RefuelDeepLinks {

    /** The intent extra the detected-fill notification carries its draft in. */
    const val EXTRA_DRAFT: String = "odo.refuel.draft"

    fun confirmDestinationFor(payload: String?): OdoDestination? {
        val draft = payload?.let(DraftPayload::decode) ?: return null
        return OdoDestination.Refuel.Confirm(draft.toInput())
    }
}
