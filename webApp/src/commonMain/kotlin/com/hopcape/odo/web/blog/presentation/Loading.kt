package com.hopcape.odo.web.blog.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import com.hopcape.odo.web.blog.domain.BlogError
import com.hopcape.odo.web.blog.presentation.state.Loadable
import com.hopcape.odo.web.blog.presentation.state.UiText
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_error_generic
import com.hopcape.odo.web.blog.resources.bl_error_not_found
import com.hopcape.odo.web.blog.resources.bl_error_offline
import com.hopcape.odo.web.blog.resources.bl_admin_signed_out
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Reads something into a [Loadable], the same way every time.
 *
 * Six screens do exactly this — go to a repository, put the answer or the failure
 * on screen, and be able to try again. Written once because the alternative is
 * six slightly different loading states, of which one forgets to reset to
 * [Loadable.Loading] on retry and shows the old error under the new content.
 */
fun <T> ViewModel.loadInto(
    target: MutableStateFlow<Loadable<T>>,
    read: suspend () -> Either<BlogError, T>,
) {
    target.value = Loadable.Loading
    viewModelScope.launch {
        target.value = read().fold(
            ifLeft = { error -> Loadable.Failed(error.asUiText(), error.isRetryable, error) },
            ifRight = { Loadable.Ready(it) },
        )
    }
}

/**
 * What to tell the reader.
 *
 * Deliberately vague about the cause. "Ye load nahi ho paya" is more useful than
 * a status code to everybody who will ever read it, and the detail that would
 * help a developer belongs in a log, not on a page.
 */
fun BlogError.asUiText(): UiText = when (this) {
    BlogError.Offline -> UiText.Resource(Res.string.bl_error_offline)
    BlogError.NotFound -> UiText.Resource(Res.string.bl_error_not_found)
    BlogError.NotSignedIn -> UiText.Resource(Res.string.bl_admin_signed_out)
    is BlogError.SignInRejected -> UiText.Resource(Res.string.bl_error_generic)
    is BlogError.Unexpected -> UiText.Resource(Res.string.bl_error_generic)
}

/**
 * Whether trying again could produce a different answer.
 *
 * A missing post is missing on the second attempt too, and a signed-out session
 * needs a sign-in rather than a retry — offering the button in either case
 * teaches readers that the button does nothing.
 */
val BlogError.isRetryable: Boolean
    get() = when (this) {
        BlogError.Offline, is BlogError.Unexpected -> true
        BlogError.NotFound, BlogError.NotSignedIn, is BlogError.SignInRejected -> false
    }
