package com.hopcape.odo.web.blog.presentation.state

import androidx.compose.runtime.Immutable

/**
 * A form on its way somewhere.
 *
 * The counterpart to [Loadable] for writes. Kept separate because the states are
 * not the same shape: a read starts loading, a write starts [Idle] and most never
 * leave it, and [Done] has nothing to carry — the confirmation is a line of copy,
 * not a value the screen renders.
 */
@Immutable
sealed interface Submission {

    /** Nothing has been sent. Every form starts here and most stay. */
    data object Idle : Submission

    /** In flight. The button is disabled and says so. */
    data object Sending : Submission

    /** Accepted. The design replaces the form with a line of copy. */
    data object Done : Submission

    @Immutable
    data class Failed(val message: UiText) : Submission
}
