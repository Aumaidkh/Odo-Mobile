package com.hopcape.odo.web.blog.presentation.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.web.blog.domain.BlogRepository
import com.hopcape.odo.web.blog.domain.model.SearchResults
import com.hopcape.odo.web.blog.presentation.asUiText
import com.hopcape.odo.web.blog.presentation.category.looksLikeEmail
import com.hopcape.odo.web.blog.presentation.loadInto
import com.hopcape.odo.web.core.presentation.state.FormField
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.Submission
import com.hopcape.odo.web.core.presentation.state.UiText
import com.hopcape.odo.web.core.presentation.state.textField
import com.hopcape.odo.web.core.presentation.state.valueOrNull
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_email_invalid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SearchEvent {
    data class EmailChanged(val value: String) : SearchEvent
    data object RequestTopic : SearchEvent
    data object Retry : SearchEvent
}

@Immutable
data class SearchUiState(
    val results: Loadable<SearchResults>,
    val email: FormField<String>,
    val request: Submission,
) {
    /** True once a search has run and come back with nothing. */
    val isDeadEnd: Boolean
        get() = results.valueOrNull?.let { it.query.isNotBlank() && it.hits.isEmpty() } ?: false

    /** True when the reader opened search without typing anything. */
    val isPrompt: Boolean
        get() = results.valueOrNull?.query.isNullOrBlank()
}

/**
 * Search results for one term.
 *
 * The term is a constructor parameter because it is part of the URL: a new search
 * is a new address and therefore a new screen, not a mutation of this one. That is
 * what makes a result page shareable and what puts each search in the back button.
 */
class SearchViewModel(
    private val query: String,
    private val blog: BlogRepository,
) : ViewModel() {

    private val results = MutableStateFlow<Loadable<SearchResults>>(Loadable.Loading)
    private val email = MutableStateFlow(textField())
    private val request = MutableStateFlow<Submission>(Submission.Idle)

    val state: StateFlow<SearchUiState> = combine(results, email, request, ::SearchUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchUiState(Loadable.Loading, textField(), Submission.Idle),
        )

    init {
        load()
    }

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.EmailChanged -> {
                email.value = email.value.update(event.value)
                if (request.value is Submission.Failed) request.value = Submission.Idle
            }

            SearchEvent.RequestTopic -> requestTopic()
            SearchEvent.Retry -> load()
        }
    }

    private fun requestTopic() {
        val address = email.value.value.trim()
        if (!address.looksLikeEmail()) {
            email.value = email.value.fail(UiText.Resource(Res.string.bl_email_invalid))
            return
        }
        request.value = Submission.Sending
        viewModelScope.launch {
            // The term goes with the address: a request that does not say what was
            // being looked for is a mailing-list signup, not a topic request.
            request.value = blog.requestTopic(address, query).fold(
                ifLeft = { Submission.Failed(it.asUiText()) },
                ifRight = { Submission.Done },
            )
        }
    }

    private fun load() = loadInto(results) { blog.search(query) }
}
