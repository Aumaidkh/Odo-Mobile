package com.hopcape.odo.web.blog.presentation.notfound

import androidx.lifecycle.ViewModel
import com.hopcape.odo.web.blog.domain.BlogRepository
import com.hopcape.odo.web.blog.domain.model.PostSummary
import com.hopcape.odo.web.blog.presentation.loadInto
import com.hopcape.odo.web.core.presentation.state.Loadable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface NotFoundEvent {
    data object Retry : NotFoundEvent
}

/**
 * The 404 page.
 *
 * It loads, which is unusual for a 404 and is the whole design: a reader who
 * followed a dead link is still a reader, and the page's job is to give them
 * somewhere to go rather than to apologise. Most-read rather than most-recent —
 * the best-performing posts are the ones most likely to be what they wanted.
 */
class NotFoundViewModel(
    private val blog: BlogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<Loadable<List<PostSummary>>>(Loadable.Loading)
    val state: StateFlow<Loadable<List<PostSummary>>> = _state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: NotFoundEvent) {
        when (event) {
            NotFoundEvent.Retry -> load()
        }
    }

    private fun load() = loadInto(_state) { blog.mostRead(MOST_READ) }

    private companion object {
        /** Three cards, which is what the design's dead-end frame holds. */
        const val MOST_READ = 3
    }
}
