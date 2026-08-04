package com.hopcape.odo.feature.billscanner.presentation.state

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText

/**
 * Where an asynchronous step has got to — an extraction, a save, a payment.
 *
 * A sealed type instead of an `isLoading` / `error` / `done` triple, which can describe
 * "loading and done and failed" at once, a state that cannot happen.
 */
@Immutable
internal sealed interface Submission {

    /** Nothing in flight; the screen is editable. */
    data object Idle : Submission

    /** Something is running; the primary action is disabled. */
    data object InFlight : Submission

    /** It failed. The screen stays editable and shows [message]. */
    @Immutable
    data class Failed(val message: UiText) : Submission

    /** It landed. */
    data object Succeeded : Submission

    val isInFlight: Boolean get() = this is InFlight

    /** The failure message, or `null` when this has not failed. */
    val error: UiText? get() = (this as? Failed)?.message
}
