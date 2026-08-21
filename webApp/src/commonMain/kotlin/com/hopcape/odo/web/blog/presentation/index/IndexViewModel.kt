package com.hopcape.odo.web.blog.presentation.index

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.web.blog.domain.BlogRepository
import com.hopcape.odo.web.blog.domain.model.Category
import com.hopcape.odo.web.blog.domain.model.IndexPage
import com.hopcape.odo.web.blog.domain.model.PostSummary
import com.hopcape.odo.web.blog.presentation.loadInto
import com.hopcape.odo.web.blog.presentation.state.Loadable
import com.hopcape.odo.web.blog.presentation.state.valueOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What the reader did on the index. */
sealed interface IndexEvent {

    /** A filter chip. Null is "All", which is not a category. */
    data class CategorySelected(val slug: String?) : IndexEvent

    data object Retry : IndexEvent
}

@Immutable
data class IndexUiState(
    val page: Loadable<IndexPage>,
    /** The chosen chip. Null means every category. */
    val selectedCategory: String?,
) {
    /** The chips, in the order the repository gave them. */
    val categories: List<Category>
        get() = page.valueOrNull?.categories.orEmpty()

    /**
     * The grid, after the chip.
     *
     * Filtered here rather than with a second query: the index has already
     * fetched these posts, and a round trip to remove four of them would blank
     * the page for longer than the filter takes.
     */
    val visible: List<PostSummary>
        get() {
            val value = page.valueOrNull ?: return emptyList()
            val all = listOfNotNull(value.lead) + value.rest
            return if (selectedCategory == null) all else all.filter { it.category.slug == selectedCategory }
        }

    /**
     * The lead story, which only exists while nothing is filtered.
     *
     * Under a chip every remaining post is equal, and promoting one of them would
     * invent an editorial decision the index never made.
     */
    val lead: PostSummary?
        get() = if (selectedCategory == null) page.valueOrNull?.lead else null

    /** The grid below the lead. */
    val grid: List<PostSummary>
        get() = if (selectedCategory == null) visible.drop(1) else visible
}

class IndexViewModel(
    private val blog: BlogRepository,
) : ViewModel() {

    private val page = MutableStateFlow<Loadable<IndexPage>>(Loadable.Loading)
    private val selected = MutableStateFlow<String?>(null)

    val state: StateFlow<IndexUiState> = combine(page, selected, ::IndexUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT),
            initialValue = IndexUiState(Loadable.Loading, null),
        )

    init {
        load()
    }

    fun onEvent(event: IndexEvent) {
        when (event) {
            is IndexEvent.CategorySelected -> selected.value = event.slug
            IndexEvent.Retry -> load()
        }
    }

    private fun load() = loadInto(page) { blog.index() }

    private companion object {
        /** Long enough to survive a recomposition, short enough not to leak. */
        const val STOP_TIMEOUT = 5_000L
    }
}
