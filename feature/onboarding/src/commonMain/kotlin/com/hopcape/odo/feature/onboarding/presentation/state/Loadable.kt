package com.hopcape.odo.feature.onboarding.presentation.state

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText

/**
 * Data that has to be fetched before it can be rendered, as the three states the owner
 * actually sees: the wait, the data, and the failure.
 *
 * Modelled as one value rather than a `value` plus an `isLoading` plus an `error` so the
 * impossible combinations can't be constructed — there is no "loaded and failed", and no
 * "not loading, no data, no reason why".
 */
@Immutable
internal sealed interface Loadable<out T> {

    /** The read is in flight (or hasn't started) — nothing to show yet. */
    data object Loading : Loadable<Nothing>

    @Immutable
    data class Ready<out T>(val value: T) : Loadable<T>

    /** The read failed; [message] is what to tell the owner, and retrying is offered. */
    @Immutable
    data class Failed(val message: UiText) : Loadable<Nothing>
}

/** The loaded value, or `null` while loading or failed. */
internal val <T> Loadable<T>.valueOrNull: T? get() = (this as? Loadable.Ready)?.value
