package com.hopcape.odo.web.blog.presentation.admin.posts

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.web.blog.domain.AdminRepository
import com.hopcape.odo.web.blog.domain.model.PostRow
import com.hopcape.odo.web.blog.domain.model.PostStatus
import com.hopcape.odo.web.blog.presentation.loadInto
import com.hopcape.odo.web.blog.presentation.state.Loadable
import com.hopcape.odo.web.blog.presentation.state.valueOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Which posts the table is showing. */
enum class PostFilter { ALL, PUBLISHED, DRAFTS }

sealed interface PostsEvent {
    data class FilterSelected(val filter: PostFilter) : PostsEvent
    data object Retry : PostsEvent
}

@Immutable
data class PostsUiState(
    val rows: Loadable<List<PostRow>>,
    val filter: PostFilter,
) {
    private val all: List<PostRow> get() = rows.valueOrNull.orEmpty()

    val visible: List<PostRow>
        get() = when (filter) {
            PostFilter.ALL -> all
            PostFilter.PUBLISHED -> all.filter { it.status == PostStatus.PUBLISHED }
            PostFilter.DRAFTS -> all.filter { it.status == PostStatus.DRAFT }
        }

    /**
     * The counts on the tabs.
     *
     * Always from the whole list, never from what is on screen — a "Drafts · 3"
     * tab that reads "Drafts · 3" only while it is selected is a tab that tells
     * you nothing when you need it.
     */
    val totalCount: Int get() = all.size
    val publishedCount: Int get() = all.count { it.status == PostStatus.PUBLISHED }
    val draftCount: Int get() = all.count { it.status == PostStatus.DRAFT }
}

class PostsViewModel(
    private val admin: AdminRepository,
) : ViewModel() {

    private val rows = MutableStateFlow<Loadable<List<PostRow>>>(Loadable.Loading)
    private val filter = MutableStateFlow(PostFilter.ALL)

    val state: StateFlow<PostsUiState> = combine(rows, filter, ::PostsUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PostsUiState(Loadable.Loading, PostFilter.ALL),
        )

    init {
        load()
    }

    fun onEvent(event: PostsEvent) {
        when (event) {
            is PostsEvent.FilterSelected -> filter.value = event.filter
            PostsEvent.Retry -> load()
        }
    }

    private fun load() = loadInto(rows) { admin.posts() }
}
