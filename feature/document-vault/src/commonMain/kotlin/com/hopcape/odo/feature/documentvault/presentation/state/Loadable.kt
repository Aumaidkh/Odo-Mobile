package com.hopcape.odo.feature.documentvault.presentation.state

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText

/**
 * Data that has to be read before it can be shown, as the three states the owner sees: the
 * wait, the data, and the failure.
 *
 * One value instead of a value plus an `isLoading` plus an `error`, so states like "loaded
 * and failed" cannot be built.
 */
@Immutable
internal sealed interface Loadable<out T> {

    /** The read is in flight or has not started. */
    data object Loading : Loadable<Nothing>

    @Immutable
    data class Ready<out T>(val value: T) : Loadable<T>

    /** The read failed. [message] is what to tell the owner. */
    @Immutable
    data class Failed(val message: UiText) : Loadable<Nothing>
}

/** The loaded value, or `null` while loading or failed. */
internal val <T> Loadable<T>.valueOrNull: T? get() = (this as? Loadable.Ready)?.value
