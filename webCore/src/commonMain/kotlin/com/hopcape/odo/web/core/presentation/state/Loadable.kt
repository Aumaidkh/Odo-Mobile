package com.hopcape.odo.web.core.presentation.state

import androidx.compose.runtime.Immutable
import com.hopcape.odo.web.core.domain.WebError

/**
 * Something that has to be read before it can be shown, as the three states a
 * reader sees: the wait, the content, and the failure.
 *
 * One value instead of a value plus an `isLoading` plus an `error`, so a state
 * like "loaded and failed" cannot be built and therefore cannot be drawn.
 */
@Immutable
sealed interface Loadable<out T> {

    /** The read is in flight, or has not started. */
    data object Loading : Loadable<Nothing>

    @Immutable
    data class Ready<out T>(val value: T) : Loadable<T>

    /**
     * The read failed.
     *
     * [message] is what to tell the reader and [retryable] decides whether the
     * screen offers to try again — a missing post is missing on the second
     * attempt too, and a button that never works teaches readers that buttons do
     * not work.
     *
     * [reason] is kept as well, because one failure is not like the others: an
     * article slug that does not exist is a reader on a dead link, and that is a
     * whole designed page rather than an error line. Only a host that can still
     * see the error can make that choice.
     */
    @Immutable
    data class Failed(
        val message: UiText,
        val retryable: Boolean = true,
        val reason: WebError? = null,
    ) : Loadable<Nothing>
}

/** True when the thing being read simply is not there. */
val Loadable<*>.isMissing: Boolean
    get() = (this as? Loadable.Failed)?.reason == WebError.NotFound

/** The value, or null while loading or failed. */
val <T> Loadable<T>.valueOrNull: T? get() = (this as? Loadable.Ready)?.value

/** Maps the loaded value, leaving the other two states alone. */
inline fun <T, R> Loadable<T>.map(transform: (T) -> R): Loadable<R> = when (this) {
    is Loadable.Ready -> Loadable.Ready(transform(value))
    is Loadable.Loading -> Loadable.Loading
    is Loadable.Failed -> this
}
