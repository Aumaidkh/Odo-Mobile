package com.hopcape.odo.web.blog.presentation.author

import androidx.lifecycle.ViewModel
import com.hopcape.odo.web.blog.domain.BlogRepository
import com.hopcape.odo.web.blog.domain.model.AuthorPage
import com.hopcape.odo.web.blog.presentation.loadInto
import com.hopcape.odo.web.blog.presentation.state.Loadable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AuthorEvent {
    data object Retry : AuthorEvent
}

/**
 * One author's page.
 *
 * No `combine`, no derived state: the page is one read and nothing on it can be
 * interacted with except the cards, which navigate. A UiState wrapper around a
 * single [Loadable] would be a layer that only ever forwards.
 */
class AuthorViewModel(
    private val slug: String,
    private val blog: BlogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<Loadable<AuthorPage>>(Loadable.Loading)
    val state: StateFlow<Loadable<AuthorPage>> = _state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: AuthorEvent) {
        when (event) {
            AuthorEvent.Retry -> load()
        }
    }

    private fun load() = loadInto(_state) { blog.author(slug) }
}
