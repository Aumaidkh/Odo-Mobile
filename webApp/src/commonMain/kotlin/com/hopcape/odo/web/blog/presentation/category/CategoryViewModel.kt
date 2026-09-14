package com.hopcape.odo.web.blog.presentation.category

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.web.blog.domain.BlogRepository
import com.hopcape.odo.web.blog.domain.model.CategoryPage
import com.hopcape.odo.web.blog.presentation.asUiText
import com.hopcape.odo.web.blog.presentation.loadInto
import com.hopcape.odo.web.core.presentation.state.FormField
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.Submission
import com.hopcape.odo.web.core.presentation.state.UiText
import com.hopcape.odo.web.core.presentation.state.textField
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_email_invalid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CategoryEvent {
    data class EmailChanged(val value: String) : CategoryEvent
    data object Subscribe : CategoryEvent
    data object Retry : CategoryEvent
}

@Immutable
data class CategoryUiState(
    val page: Loadable<CategoryPage>,
    val email: FormField<String>,
    val subscription: Submission,
) {
    /**
     * Whether to draw the "more coming" block.
     *
     * The design shows it on a category with one article, not on one with six.
     * The threshold is here rather than in the screen because it is a content
     * judgement, and a screen that owned it would have to be edited to change it.
     */
    val isThin: Boolean
        get() = (page as? Loadable.Ready)?.value?.posts?.size?.let { it <= THIN_THRESHOLD } ?: false

    private companion object {
        const val THIN_THRESHOLD = 2
    }
}

class CategoryViewModel(
    private val slug: String,
    private val blog: BlogRepository,
) : ViewModel() {

    private val page = MutableStateFlow<Loadable<CategoryPage>>(Loadable.Loading)
    private val email = MutableStateFlow(textField())
    private val subscription = MutableStateFlow<Submission>(Submission.Idle)

    val state: StateFlow<CategoryUiState> = combine(page, email, subscription, ::CategoryUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoryUiState(Loadable.Loading, textField(), Submission.Idle),
        )

    init {
        load()
    }

    fun onEvent(event: CategoryEvent) {
        when (event) {
            is CategoryEvent.EmailChanged -> {
                email.value = email.value.update(event.value)
                // Typing after a rejection clears it. The next submit will say so
                // again if the address is still wrong.
                if (subscription.value is Submission.Failed) subscription.value = Submission.Idle
            }

            CategoryEvent.Subscribe -> subscribe()
            CategoryEvent.Retry -> load()
        }
    }

    private fun subscribe() {
        val address = email.value.value.trim()
        if (!address.looksLikeEmail()) {
            email.value = email.value.fail(UiText.Resource(Res.string.bl_email_invalid))
            return
        }
        subscription.value = Submission.Sending
        viewModelScope.launch {
            subscription.value = blog.subscribe(address).fold(
                ifLeft = { Submission.Failed(it.asUiText()) },
                ifRight = { Submission.Done },
            )
        }
    }

    private fun load() = loadInto(page) { blog.category(slug) }
}

/**
 * Enough to catch a typo, not enough to reject a real address.
 *
 * Validating email properly is a losing game; the server is what actually decides
 * whether an address exists, and this is only here so a form does not make a
 * round trip for a value with no `@` in it.
 */
internal fun String.looksLikeEmail(): Boolean {
    val at = indexOf('@')
    return at > 0 && lastIndexOf('.') > at + 1 && !endsWith(".")
}
