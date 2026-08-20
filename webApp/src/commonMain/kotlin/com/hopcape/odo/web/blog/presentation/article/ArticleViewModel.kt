package com.hopcape.odo.web.blog.presentation.article

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.web.blog.domain.BlogRepository
import com.hopcape.odo.web.blog.domain.model.Article
import com.hopcape.odo.web.blog.presentation.loadInto
import com.hopcape.odo.web.blog.presentation.state.Loadable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface ArticleEvent {

    /** A line in the contents rail. */
    data class SectionSelected(val id: String) : ArticleEvent

    /** The collapsed contents strip on a phone. */
    data object ContentsToggled : ArticleEvent

    data object Retry : ArticleEvent
}

@Immutable
data class ArticleUiState(
    val article: Loadable<Article>,
    /** Which rail line is marked. Null until something has been jumped to. */
    val activeSection: String?,
    /** Whether the phone's contents strip is open. Ignored on a wide screen. */
    val contentsExpanded: Boolean,
)

/**
 * One article.
 *
 * The slug arrives as a constructor parameter, not from a saved-state handle —
 * the same rule the app's features follow. Here the reason is simpler than it is
 * there: the slug is in the URL, and the URL is what rebuilt this screen.
 */
class ArticleViewModel(
    private val slug: String,
    private val blog: BlogRepository,
) : ViewModel() {

    private val article = MutableStateFlow<Loadable<Article>>(Loadable.Loading)
    private val activeSection = MutableStateFlow<String?>(null)
    private val contentsExpanded = MutableStateFlow(false)

    val state: StateFlow<ArticleUiState> =
        combine(article, activeSection, contentsExpanded, ::ArticleUiState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ArticleUiState(Loadable.Loading, null, false),
            )

    init {
        load()
    }

    fun onEvent(event: ArticleEvent) {
        when (event) {
            is ArticleEvent.SectionSelected -> {
                activeSection.value = event.id
                // Picking a section on a phone is also the gesture that closes
                // the strip; leaving it open would cover what was jumped to.
                contentsExpanded.value = false
            }

            ArticleEvent.ContentsToggled -> contentsExpanded.value = !contentsExpanded.value
            ArticleEvent.Retry -> load()
        }
    }

    private fun load() = loadInto(article) { blog.article(slug) }
}
