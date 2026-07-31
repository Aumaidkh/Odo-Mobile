package com.hopcape.odo.feature.servicelog.presentation.state

import com.hopcape.odo.core.designsystem.text.UiText

/**
 * Where a write has got to — the one state a screen that submits something can be in.
 *
 * A sealed type rather than the `isSubmitting` / `error` / `submitted` triple it replaces:
 * those three booleans can describe "submitting **and** already submitted **and** failed",
 * which is not a thing that can happen, and every screen rendering them had to decide which
 * one wins. Here the `when` is exhaustive and the impossible combinations don't exist.
 */
internal sealed interface Submission {

    /** Nothing in flight — the editable state. */
    data object Idle : Submission

    /** A write is running; the primary action is disabled and shows progress. */
    data object InFlight : Submission

    /** The write failed with a reason worth showing. The screen stays editable. */
    data class Failed(val message: UiText) : Submission

    /** The write landed — the screen shows its confirmation. */
    data object Succeeded : Submission

    val isInFlight: Boolean get() = this is InFlight

    val isSucceeded: Boolean get() = this is Succeeded

    /** The failure message, or `null` when this submission hasn't failed. */
    val error: UiText? get() = (this as? Failed)?.message
}
