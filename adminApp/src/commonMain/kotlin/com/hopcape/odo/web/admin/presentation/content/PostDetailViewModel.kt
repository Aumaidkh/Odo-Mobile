package com.hopcape.odo.web.admin.presentation.content

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import com.hopcape.odo.web.admin.domain.BlogCategory
import com.hopcape.odo.web.admin.domain.BlogPost
import com.hopcape.odo.web.admin.domain.ContentRepository
import com.hopcape.odo.web.admin.domain.PostBlock
import com.hopcape.odo.web.admin.domain.PostDetail
import com.hopcape.odo.web.admin.presentation.asUiText
import com.hopcape.odo.web.admin.presentation.loadInto
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_content_saved
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.UiText
import com.hopcape.odo.web.core.presentation.state.valueOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PostDetailEvent {
    data object Refresh : PostDetailEvent
    data object EditStarted : PostDetailEvent
    data object EditCancelled : PostDetailEvent
    data class TitleChanged(val value: String) : PostDetailEvent
    data class DekChanged(val value: String) : PostDetailEvent
    data class SlugChanged(val value: String) : PostDetailEvent
    data class CategoryChanged(val slug: String?) : PostDetailEvent
    data class SeoTitleChanged(val value: String) : PostDetailEvent
    data class MetaDescriptionChanged(val value: String) : PostDetailEvent
    data object Saved : PostDetailEvent
    data object PublishToggled : PostDetailEvent

    /** Body editing. The preview turns into a stack of text boxes and back. */
    data object BodyEditStarted : PostDetailEvent
    data object BodyEditCancelled : PostDetailEvent
    data class BlockChanged(val index: Int, val text: String) : PostDetailEvent
    data class BlockLabelChanged(val index: Int, val label: String) : PostDetailEvent
    data class BlockMoved(val index: Int, val by: Int) : PostDetailEvent
    data class BlockRemoved(val index: Int) : PostDetailEvent
    data class BlockAdded(val kind: PostBlock.Kind) : PostDetailEvent
    data object BodySaved : PostDetailEvent

    data object MessageDismissed : PostDetailEvent
}

/** The editable half, held apart from the loaded post so Cancel is a discard. */
@Immutable
data class MetaDraft(
    val title: String = "",
    val dek: String = "",
    val slug: String = "",
    val categorySlug: String? = null,
    val seoTitle: String = "",
    val metaDescription: String = "",
) {
    /**
     * A published post must keep a slug — the database refuses one without, and the
     * URL is already out in the world.
     */
    fun canSubmit(published: Boolean): Boolean =
        title.isNotBlank() && (!published || slug.isNotBlank())
}

@Immutable
data class PostDetailUiState(
    val detail: Loadable<PostDetail> = Loadable.Loading,
    val categories: List<BlogCategory> = emptyList(),
    val draft: MetaDraft? = null,
    /** The body being edited, or null while it is only being read. */
    val bodyDraft: List<PostBlock>? = null,
    val busy: Boolean = false,
    val message: UiText? = null,
) {
    val value: PostDetail? get() = detail.valueOrNull
    val editing: Boolean get() = draft != null
    val editingBody: Boolean get() = bodyDraft != null
    val isPublished: Boolean get() = value?.post?.status == BlogPost.PUBLISHED

    /** What the preview draws: the draft while editing, the stored body otherwise. */
    val body: List<PostBlock> get() = bodyDraft ?: value?.body.orEmpty()
}

/**
 * One post, read and edited inside the panel.
 *
 * Its own view model rather than a mode of [ContentViewModel]: the list holds
 * twenty rows and a page index, this holds one post and an edit draft, and folding
 * them together would mean every keystroke in a title box recomposing the list.
 *
 * The body is edited here too, block by block — the panel is where a post is worked
 * on, and sending somebody to another origin's sign-in page to fix a typo is not
 * editing from the panel.
 *
 * What it does *not* do is author the kinds it cannot represent. An image is a URL
 * and a table is a grid of cells; both survive a save untouched (see
 * [PostBlock.raw]) but neither is editable here, because a text box that claimed to
 * edit either would be lying about what it was going to write.
 */
class PostDetailViewModel(
    private val content: ContentRepository,
    private val postId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(PostDetailUiState())
    val state: StateFlow<PostDetailUiState> = _state.asStateFlow()

    private val detail = MutableStateFlow<Loadable<PostDetail>>(Loadable.Loading)

    init {
        viewModelScope.launch { detail.collect { v -> _state.value = _state.value.copy(detail = v) } }
        load()
        viewModelScope.launch {
            content.categories().onRight { list -> _state.value = _state.value.copy(categories = list) }
        }
    }

    fun onEvent(event: PostDetailEvent) {
        when (event) {
            PostDetailEvent.Refresh -> load()

            PostDetailEvent.EditStarted -> {
                val current = _state.value.value ?: return
                _state.value = _state.value.copy(
                    draft = MetaDraft(
                        title = current.post.title,
                        dek = current.dek,
                        slug = current.post.slug.orEmpty(),
                        categorySlug = current.categorySlug,
                        seoTitle = current.seoTitle,
                        metaDescription = current.metaDescription,
                    ),
                )
            }

            PostDetailEvent.EditCancelled -> _state.value = _state.value.copy(draft = null)

            is PostDetailEvent.TitleChanged -> edit { it.copy(title = event.value) }
            is PostDetailEvent.DekChanged -> edit { it.copy(dek = event.value) }
            is PostDetailEvent.SlugChanged -> edit { it.copy(slug = event.value) }
            is PostDetailEvent.CategoryChanged -> edit { it.copy(categorySlug = event.slug) }
            is PostDetailEvent.SeoTitleChanged -> edit { it.copy(seoTitle = event.value) }
            is PostDetailEvent.MetaDescriptionChanged -> edit { it.copy(metaDescription = event.value) }

            PostDetailEvent.Saved -> {
                val draft = _state.value.draft ?: return
                if (!draft.canSubmit(_state.value.isPublished)) return
                write {
                    content.saveMeta(
                        id = postId,
                        title = draft.title.trim(),
                        dek = draft.dek.trim(),
                        // Blank is stored as null, not as the empty string: the
                        // unique index and the published-needs-a-slug constraint
                        // both treat null as "none" and "" as a real value.
                        slug = draft.slug.trim().ifBlank { null },
                        categorySlug = draft.categorySlug,
                        seoTitle = draft.seoTitle.trim(),
                        metaDescription = draft.metaDescription.trim(),
                    )
                }
            }

            PostDetailEvent.PublishToggled -> {
                val current = _state.value.value ?: return
                write { content.setPublished(postId, !current.post.isPublished) }
            }

            PostDetailEvent.BodyEditStarted ->
                _state.value = _state.value.copy(bodyDraft = _state.value.value?.body.orEmpty())

            PostDetailEvent.BodyEditCancelled -> _state.value = _state.value.copy(bodyDraft = null)

            // `edited = true` is what tells the repository to rebuild this block
            // rather than write its stored form back. Without it a save would
            // flatten every block in the post, not the ones somebody touched.
            is PostDetailEvent.BlockChanged -> editBlock(event.index) {
                it.copy(text = event.text, edited = true)
            }

            is PostDetailEvent.BlockLabelChanged -> editBlock(event.index) {
                it.copy(label = event.label, edited = true)
            }

            is PostDetailEvent.BlockMoved -> {
                val blocks = _state.value.bodyDraft ?: return
                val to = event.index + event.by
                if (to !in blocks.indices) return
                val reordered = blocks.toMutableList()
                reordered.add(to, reordered.removeAt(event.index))
                // Moving does not touch a block's content, so `edited` is left
                // alone — a reorder must not flatten the blocks it moves past.
                _state.value = _state.value.copy(bodyDraft = reordered)
            }

            is PostDetailEvent.BlockRemoved -> {
                val blocks = _state.value.bodyDraft ?: return
                if (event.index !in blocks.indices) return
                _state.value = _state.value.copy(bodyDraft = blocks.filterIndexed { i, _ -> i != event.index })
            }

            is PostDetailEvent.BlockAdded -> {
                val blocks = _state.value.bodyDraft ?: return
                _state.value = _state.value.copy(
                    bodyDraft = blocks + PostBlock(kind = event.kind, text = "", edited = true),
                )
            }

            PostDetailEvent.BodySaved -> {
                val blocks = _state.value.bodyDraft ?: return
                write { content.saveBody(postId, blocks) }
            }

            PostDetailEvent.MessageDismissed -> _state.value = _state.value.copy(message = null)
        }
    }

    private fun editBlock(index: Int, block: (PostBlock) -> PostBlock) {
        val blocks = _state.value.bodyDraft ?: return
        if (index !in blocks.indices) return
        _state.value = _state.value.copy(
            bodyDraft = blocks.mapIndexed { i, existing -> if (i == index) block(existing) else existing },
        )
    }

    private fun edit(block: (MetaDraft) -> MetaDraft) {
        _state.value = _state.value.copy(draft = _state.value.draft?.let(block))
    }

    private fun load() = loadInto(detail) { content.detail(postId) }

    private fun write(action: suspend () -> Either<WebError, Unit>) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            action().fold(
                ifLeft = { e -> _state.value = _state.value.copy(busy = false, message = e.asUiText()) },
                ifRight = {
                    _state.value = _state.value.copy(
                        busy = false,
                        draft = null,
                        bodyDraft = null,
                        message = UiText.Resource(Res.string.ad_content_saved),
                    )
                    load()
                },
            )
        }
    }
}
