package com.hopcape.odo.feature.auth.presentation.state

import com.hopcape.odo.core.designsystem.text.UiText

/**
 * Where a request has got to — the one state a screen that submits something can be in.
 *
 * The same sealed shape the other features use, for the same reason: an
 * `isSubmitting`/`error`/`done` triple can describe combinations that cannot happen, and
 * every screen rendering them has to decide which one wins.
 */
internal sealed interface Submission {

    /** Nothing in flight — the editable state. */
    data object Idle : Submission

    /** A request is running; the primary action is disabled and shows progress. */
    data object InFlight : Submission

    /** It failed with a reason worth showing. The screen stays usable. */
    data class Failed(val message: UiText) : Submission

    val isInFlight: Boolean get() = this is InFlight

    /** The failure message, or null when this submission hasn't failed. */
    val error: UiText? get() = (this as? Failed)?.message
}
