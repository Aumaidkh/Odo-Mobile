package com.hopcape.odo.web.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_error_conflict
import com.hopcape.odo.web.admin.resources.ad_error_generic
import com.hopcape.odo.web.admin.resources.ad_error_not_found
import com.hopcape.odo.web.admin.resources.ad_error_offline
import com.hopcape.odo.web.admin.resources.ad_error_no_admins
import com.hopcape.odo.web.admin.resources.ad_error_not_staff
import com.hopcape.odo.web.admin.resources.ad_error_signed_out
import com.hopcape.odo.web.admin.resources.ad_error_sign_in_unavailable
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.UiText
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Reads something into a [Loadable], the same way every time.
 *
 * A copy of the blog's, deliberately, and not something `:webCore` owns. The
 * mapping below is what makes it useful and the mapping is per-app: the same
 * [WebError.NotPermitted] reads as "you are not an author" there and "you are not
 * staff" here. Sharing the function would mean sharing the strings.
 *
 * The skeleton is only for a section holding nothing. A re-read leaves the rows
 * that are already drawn up until the answer lands.
 */
suspend fun <T> readInto(
    target: MutableStateFlow<Loadable<T>>,
    read: suspend () -> Either<WebError, T>,
) {
    if (target.value !is Loadable.Ready) target.value = Loadable.Loading
    target.value = read().fold(
        ifLeft = { error -> Loadable.Failed(error.asUiText(), error.isRetryable, error) },
        ifRight = { Loadable.Ready(it) },
    )
}

/**
 * Every read a section makes, run together, with [busy] raised until the last of
 * them answers. [busy] is what the header's reload control watches, since on a
 * re-read the rows below it no longer change to say the button was pressed.
 */
fun ViewModel.readAll(busy: (Boolean) -> Unit, vararg reads: suspend () -> Unit) {
    busy(true)
    viewModelScope.launch {
        // `finally`, because a read that throws would otherwise leave the section's
        // controls disabled with nothing left to turn them back on.
        try {
            coroutineScope { reads.forEach { read -> launch { read() } } }
        } finally {
            busy(false)
        }
    }
}

/**
 * What to tell whoever is looking at it.
 *
 * Vaguer about the cause than a developer would like, and specific about what to
 * do next. The detail that would help a developer belongs in a log; the person at
 * the keyboard needs to know whether to retry, sign in, or call somebody.
 */
fun WebError.asUiText(): UiText = when (this) {
    WebError.Offline -> UiText.Resource(Res.string.ad_error_offline)
    WebError.NotFound -> UiText.Resource(Res.string.ad_error_not_found)
    WebError.Conflict -> UiText.Resource(Res.string.ad_error_conflict)
    WebError.NotSignedIn -> UiText.Resource(Res.string.ad_error_signed_out)
    WebError.NotPermitted -> UiText.Resource(Res.string.ad_error_not_staff)
    WebError.NotConfigured -> UiText.Resource(Res.string.ad_error_no_admins)
    WebError.SignInUnavailable -> UiText.Resource(Res.string.ad_error_sign_in_unavailable)
    is WebError.SignInRejected -> UiText.Resource(Res.string.ad_error_generic)
    is WebError.Unexpected -> UiText.Resource(Res.string.ad_error_generic)
}

/**
 * Whether trying again could produce a different answer.
 *
 * Not being staff is not being staff on the second attempt too, and a signed-out
 * session needs a sign-in rather than a retry — offering the button in either case
 * teaches people that the button does nothing.
 */
val WebError.isRetryable: Boolean
    get() = when (this) {
        WebError.Offline, is WebError.Unexpected -> true
        WebError.NotFound,
        WebError.Conflict,
        WebError.NotSignedIn,
        WebError.NotPermitted,
        WebError.NotConfigured,
        WebError.SignInUnavailable,
        is WebError.SignInRejected,
        -> false
    }
