package com.hopcape.odo.feature.billcheck.presentation.share

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.shared.Amount

/**
 * What the card says, and whether something is being made of it.
 *
 * The figures arrive with the destination rather than from a second read, so the card cannot
 * show a different number from the screen it was opened off.
 */
@Immutable
internal data class ShareCardUiState(
    val amount: Amount = Amount.ZERO,
    val flagged: Int = 0,
    val lines: Int = 0,
    /** A capture and a file write are in flight. Both buttons wait for it. */
    val working: Boolean = false,
)

internal sealed interface ShareCardEvent {

    data object BackClicked : ShareCardEvent

    /**
     * The card, already drawn to PNG bytes by the screen that owns the pixels.
     *
     * Null when the capture failed, which the ViewModel reports the same way a failed write
     * is reported — the owner tapped a button and got no card either way.
     */
    class SendOnWhatsAppClicked(val png: ByteArray?) : ShareCardEvent

    class SaveClicked(val png: ByteArray?) : ShareCardEvent
}

internal sealed interface ShareCardEffect {

    data object NavigateBack : ShareCardEffect

    /**
     * The card is written and ready to hand over.
     *
     * The message beside it is assembled by the route, which is where the strings are — and
     * it is the same headline the result screen shows, read from the same figures.
     */
    data class ShareImage(val storageKey: String) : ShareCardEffect

    data object Saved : ShareCardEffect

    /** The capture, the write or the copy failed. One message covers all three. */
    data object Failed : ShareCardEffect
}
