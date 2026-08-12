package com.hopcape.odo.feature.garage.presentation.state

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText

/**
 * Where a write has got to. Used by the screens that add, edit or remove a car and by the
 * odometer sheet.
 *
 * A sealed type instead of an `isSaving` / `error` / `saved` triple, which can describe
 * "saving and saved and failed" — a state that cannot happen.
 */
@Immutable
internal sealed interface Submission {

    /** Nothing in flight; the screen is editable. */
    data object Idle : Submission

    /** A write is running; the primary action is disabled. */
    data object InFlight : Submission

    /** The write failed. The screen stays editable and shows [message]. */
    @Immutable
    data class Failed(val message: UiText) : Submission

    /** The write landed. */
    data object Succeeded : Submission

    val isInFlight: Boolean get() = this is InFlight

    /** The failure message, or `null` when this submission has not failed. */
    val error: UiText? get() = (this as? Failed)?.message
}
