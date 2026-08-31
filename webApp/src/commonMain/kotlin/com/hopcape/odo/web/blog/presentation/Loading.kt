package com.hopcape.odo.web.blog.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.UiText
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_error_generic
import com.hopcape.odo.web.blog.resources.bl_error_not_found
import com.hopcape.odo.web.blog.resources.bl_error_offline
import com.hopcape.odo.web.blog.resources.bl_admin_no_authors
import com.hopcape.odo.web.blog.resources.bl_admin_not_an_author
import com.hopcape.odo.web.blog.resources.bl_admin_sign_in_unavailable
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
    read: suspend () -> Either<WebError, T>,
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
 * Deliberately vague about the cause. "This did not load" is more useful than
 * a status code to everybody who will ever read it, and the detail that would
 * help a developer belongs in a log, not on a page.
 */
fun WebError.asUiText(): UiText = when (this) {
    WebError.Offline -> UiText.Resource(Res.string.bl_error_offline)
    WebError.NotFound -> UiText.Resource(Res.string.bl_error_not_found)
    WebError.NotSignedIn -> UiText.Resource(Res.string.bl_admin_signed_out)
    WebError.NotPermitted -> UiText.Resource(Res.string.bl_admin_not_an_author)
    WebError.NotConfigured -> UiText.Resource(Res.string.bl_admin_no_authors)
    WebError.SignInUnavailable -> UiText.Resource(Res.string.bl_admin_sign_in_unavailable)
    is WebError.SignInRejected -> UiText.Resource(Res.string.bl_error_generic)
    is WebError.Unexpected -> UiText.Resource(Res.string.bl_error_generic)
}

/**
 * Whether trying again could produce a different answer.
 *
 * A missing post is missing on the second attempt too, and a signed-out session
 * needs a sign-in rather than a retry — offering the button in either case
 * teaches readers that the button does nothing.
 */
val WebError.isRetryable: Boolean
    get() = when (this) {
        WebError.Offline, is WebError.Unexpected -> true
        WebError.NotFound,
        WebError.NotSignedIn,
        WebError.NotPermitted,
        WebError.NotConfigured,
        WebError.SignInUnavailable,
        is WebError.SignInRejected,
        -> false
    }
