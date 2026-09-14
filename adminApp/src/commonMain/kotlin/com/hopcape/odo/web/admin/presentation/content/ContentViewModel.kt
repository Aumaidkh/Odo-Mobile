package com.hopcape.odo.web.admin.presentation.content

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import com.hopcape.odo.web.admin.domain.BlogCategory
import com.hopcape.odo.web.admin.domain.BlogPost
import com.hopcape.odo.web.admin.domain.ContentRepository
import com.hopcape.odo.web.admin.presentation.asUiText
import com.hopcape.odo.web.admin.presentation.readAll
import com.hopcape.odo.web.admin.presentation.readInto
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_content_created
import com.hopcape.odo.web.admin.resources.ad_content_deleted
import com.hopcape.odo.web.admin.resources.ad_content_slug_taken
import com.hopcape.odo.web.admin.resources.ad_content_published
import com.hopcape.odo.web.admin.resources.ad_content_unpublished
import com.hopcape.odo.web.admin.ui.component.Page
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.presentation.state.FormField
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.textField
import com.hopcape.odo.web.core.presentation.state.UiText
import com.hopcape.odo.web.core.presentation.state.valueOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

sealed interface ContentEvent {
    data object Refresh : ContentEvent
    data class SearchChanged(val value: String) : ContentEvent
    data class PublishToggled(val post: BlogPost) : ContentEvent
    /** Two steps: ask, then do. Nothing removes a post on one click. */
    data class DeleteRequested(val post: BlogPost) : ContentEvent
    data object DeleteDismissed : ContentEvent
    data object DeleteConfirmed : ContentEvent
    data object NextPage : ContentEvent
    data object PreviousPage : ContentEvent
    data object CreateRequested : ContentEvent
    data object CreateDismissed : ContentEvent
    data class DraftTitleChanged(val value: String) : ContentEvent
    data class DraftDekChanged(val value: String) : ContentEvent
    data class DraftSlugChanged(val value: String) : ContentEvent
    data class DraftCategoryChanged(val slug: String?) : ContentEvent
    data object DraftSubmitted : ContentEvent
    data object MessageDismissed : ContentEvent
}

/** The create-a-draft form. Null when it is not open. */
@Immutable
data class NewPostDraft(
    val title: FormField<String> = textField(),
    val dek: FormField<String> = textField(),
    val slug: FormField<String> = textField(),
    val categorySlug: String? = null,
    /** True once somebody edits the slug, so it stops following the title. */
    val slugEdited: Boolean = false,
    val slugError: UiText? = null,
) {
    val canSubmit: Boolean get() = title.value.isNotBlank() && slug.value.isNotBlank()
}

@Immutable
data class ContentUiState(
    val posts: Loadable<List<BlogPost>> = Loadable.Loading,
    val categories: List<BlogCategory> = emptyList(),
    val draft: NewPostDraft? = null,
    val search: String = "",
    val page: Page = Page(0),
    val pendingDelete: BlogPost? = null,
    val busy: Boolean = false,
    val message: UiText? = null,
) {
    val matching: List<BlogPost>
        get() {
            val term = search.trim()
            if (term.isEmpty()) return posts.valueOrNull.orEmpty()
            return posts.valueOrNull.orEmpty().filter {
                it.title.contains(term, ignoreCase = true) ||
                    it.slug?.contains(term, ignoreCase = true) == true ||
                    it.authorName?.contains(term, ignoreCase = true) == true
            }
        }

    val visible: List<BlogPost> get() = page.windowOf(matching)

    val draftCount: Int get() = posts.valueOrNull.orEmpty().count { !it.isPublished }

    internal companion object {
        /**
         * Slugs the blog's own router owns.
         *
         * A post slugged `search` would take the search page's URL — `:webApp`'s
         * RESERVED_SLUGS says the same thing, and this is the copy that stops one
         * being created in the first place.
         */
        val RESERVED = setOf("category", "author", "search", "admin")
    }
}

/**
 * The blog's post list.
 *
 * Publishing and unpublishing, not editing. The editor lives in `:webApp` and
 * moving it is the whole of #370; what this covers is the part somebody needs at
 * short notice — putting a page live, or taking one down.
 */
class ContentViewModel(
    private val content: ContentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ContentUiState())
    val state: StateFlow<ContentUiState> = _state.asStateFlow()

    private val posts = MutableStateFlow<Loadable<List<BlogPost>>>(Loadable.Loading)

    init {
        viewModelScope.launch { posts.collect { v -> _state.value = _state.value.copy(posts = v) } }
        load()
    }

    fun onEvent(event: ContentEvent) {
        when (event) {
            ContentEvent.Refresh -> load()

            is ContentEvent.SearchChanged ->
                _state.value = _state.value.copy(search = event.value, page = _state.value.page.reset())

            is ContentEvent.PublishToggled -> write(
                if (event.post.isPublished) Res.string.ad_content_unpublished else Res.string.ad_content_published,
            ) { content.setPublished(event.post.id, !event.post.isPublished) }

            is ContentEvent.DeleteRequested -> _state.value = _state.value.copy(pendingDelete = event.post)
            ContentEvent.DeleteDismissed -> _state.value = _state.value.copy(pendingDelete = null)
            ContentEvent.DeleteConfirmed -> {
                val post = _state.value.pendingDelete ?: return
                _state.value = _state.value.copy(pendingDelete = null)
                write(Res.string.ad_content_deleted) { content.delete(post.id) }
            }

            ContentEvent.NextPage -> if (_state.value.page.hasNext(_state.value.matching.size)) {
                _state.value = _state.value.copy(page = _state.value.page.copy(index = _state.value.page.index + 1))
            }

            ContentEvent.PreviousPage -> if (_state.value.page.hasPrevious) {
                _state.value = _state.value.copy(page = _state.value.page.copy(index = _state.value.page.index - 1))
            }

            ContentEvent.CreateRequested -> _state.value = _state.value.copy(draft = NewPostDraft())
            ContentEvent.CreateDismissed -> _state.value = _state.value.copy(draft = null)

            // The slug follows the title until somebody touches it. A URL derived
            // from a headline is right almost every time, and the times it is not
            // are exactly the times somebody edits it.
            is ContentEvent.DraftTitleChanged -> editDraft {
                copy(
                    title = title.update(event.value),
                    slug = if (slugEdited) slug else slug.update(slugify(event.value)),
                )
            }

            is ContentEvent.DraftDekChanged -> editDraft { copy(dek = dek.update(event.value)) }

            is ContentEvent.DraftSlugChanged -> editDraft {
                copy(slug = slug.update(slugify(event.value)), slugEdited = true, slugError = null)
            }

            is ContentEvent.DraftCategoryChanged -> editDraft { copy(categorySlug = event.slug) }

            ContentEvent.DraftSubmitted -> submitDraft()

            ContentEvent.MessageDismissed -> _state.value = _state.value.copy(message = null)
        }
    }

    private fun load() = readAll(
        { busy -> _state.value = _state.value.copy(busy = busy) },
        { readInto(posts) { content.posts() } },
        { content.categories().onRight { _state.value = _state.value.copy(categories = it) } },
    )

    private fun editDraft(block: NewPostDraft.() -> NewPostDraft) {
        _state.value = _state.value.copy(draft = _state.value.draft?.block())
    }

    /**
     * Create, after checking the slug is free.
     *
     * `blog_posts.slug` is unique, so the database would catch this — but only
     * after a round trip, and with a message about a constraint rather than about
     * the field somebody is looking at.
     */
    private fun submitDraft() {
        val draft = _state.value.draft ?: return
        if (!draft.canSubmit) return

        val slug = draft.slug.value.trim()
        if (slug in ContentUiState.RESERVED || _state.value.posts.valueOrNull.orEmpty().any { it.slug == slug }) {
            editDraft { copy(slugError = UiText.Resource(Res.string.ad_content_slug_taken)) }
            return
        }

        write(Res.string.ad_content_created) {
            content.createDraft(
                title = draft.title.value.trim(),
                dek = draft.dek.value.trim(),
                slug = slug,
                categorySlug = draft.categorySlug,
            )
        }.also { _state.value = _state.value.copy(draft = null) }
    }

    /**
     * A URL out of a headline.
     *
     * The same shape `:webApp`'s router expects: lowercase, alphanumerics and
     * hyphens, no leading or trailing hyphen.
     */
    private fun slugify(value: String): String =
        value.lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')

    private fun write(done: StringResource, action: suspend () -> Either<WebError, Unit>) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            action().fold(
                ifLeft = { error -> _state.value = _state.value.copy(busy = false, message = error.asUiText()) },
                ifRight = {
                    _state.value = _state.value.copy(busy = false, message = UiText.Resource(done))
                    load()
                },
            )
        }
    }
}
