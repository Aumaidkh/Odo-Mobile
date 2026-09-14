package com.hopcape.odo.feature.challan.presentation.lookup

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText

/**
 * Display state for the buyer's check.
 *
 * [notFound] swaps the whole body for the "No vehicle found" answer (mockup 9) while the
 * typed plate stays in [plate], so "Edit the number" returns to exactly what they typed.
 */
@Immutable
internal data class ChallanLookupUiState(
    /** As typed, uppercased — spaces allowed; normalization happens on submit. */
    val plate: String = "",
    val checking: Boolean = false,
    /** Input or source trouble, under the field. */
    val error: UiText? = null,
    val notFound: NotFoundState? = null,
) {
    /** A plate plausibly complete enough to send — the field's own gate, not validation. */
    val canCheck: Boolean get() = plate.filterNot { it.isWhitespace() }.length >= MIN_PLATE_LENGTH && !checking

    private companion object {
        const val MIN_PLATE_LENGTH = 6
    }
}

/** The "No vehicle found" answer, with the plate as it was sent. */
@Immutable
internal data class NotFoundState(val plateDisplay: String)
